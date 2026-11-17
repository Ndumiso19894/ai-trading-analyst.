from io import BytesIO
import pandas as pd
from fastapi.testclient import TestClient
from PIL import Image
from app.main import app
import signals.storage as signal_storage
import pytest

client=TestClient(app)

@pytest.fixture(autouse=True)
def isolated_signal_db(tmp_path,monkeypatch):
    monkeypatch.setattr(signal_storage,"DB_PATH",str(tmp_path/"test-signals.sqlite3"))

def csv_bytes(n=320):
    x=range(n); close=[1900+i*.2 for i in x]
    frame=pd.DataFrame({"timestamp":pd.date_range("2024-01-01",periods=n,freq="h",tz="UTC").astype(str),"open":[v-.1 for v in close],"high":[v+1 for v in close],"low":[v-1 for v in close],"close":close,"volume":[100]*n})
    return frame.to_csv(index=False).encode()

def test_csv_analysis_upload_and_history():
    r=client.post("/api/analysis/csv?timeframe=1h",files={"file":("history.csv",csv_bytes(),"text/csv")})
    assert r.status_code==200, r.text
    assert r.json()["signal"] in {"BUY","SELL","WAIT"}
    assert r.json()["source"].startswith("CSV")
    h=client.get("/api/signals")
    assert h.status_code==200 and len(h.json()["signals"])>=1

def test_csv_bad_schema_is_rejected():
    r=client.post("/api/market/csv",files={"file":("bad.csv",b"x,y\n1,2\n","text/csv")})
    assert r.status_code==422

def test_screenshot_upload_is_explicitly_abstaining():
    image=Image.new("RGB",(240,160),(20,30,40)); buf=BytesIO(); image.save(buf,format="PNG")
    r=client.post("/api/screenshot/analyze",files={"file":("chart.png",buf.getvalue(),"image/png")})
    assert r.status_code==200, r.text
    payload=r.json()
    assert payload["signal"]=="WAIT" and payload["visual_summary"]["width"]>0
    assert "cannot reliably" in payload["assessment"]

def test_risk_calculation():
    r=client.post("/api/risk/calculate",json={"entry":2000,"stop_loss":1990,"take_profit":2020,"account_balance":10000,"risk_percent":1,"contract_size":1})
    assert r.status_code==200 and r.json()["risk_reward"]==2
    assert r.json()["potential_loss"]==100

def test_backtest_requires_and_reports_chronological_split():
    r=client.post("/api/backtest?timeframe=1h",files={"file":("history.csv",csv_bytes(500),"text/csv")})
    assert r.status_code==200, r.text
    body=r.json()
    assert body["split"]["method"].startswith("chronological 70/30")
    assert body["out_of_sample"]["evaluation_start_index"]==350
    assert "max_consecutive_wins" in body

def test_data_api_restricts_unconfigured_ticker():
    r=client.get("/api/market/candles?symbol=EURUSD=X")
    assert r.status_code==400
