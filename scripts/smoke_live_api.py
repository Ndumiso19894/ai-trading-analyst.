#!/usr/bin/env python3
"""Phone-deployment smoke suite. Only run against an explicitly supplied live HTTPS API."""
from __future__ import annotations
import json
import os
import struct
import time
import urllib.error
import urllib.request
import uuid
import zlib

BASE = ""

def request(path: str, method="GET", body=None, headers=None, timeout=150):
    req=urllib.request.Request(BASE+path, data=body, headers=headers or {}, method=method)
    attempts=6 if path=="/health" and method=="GET" else 1
    last_error=None
    for attempt in range(attempts):
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                raw=resp.read()
                print(f"PASS {method} {path}: HTTP {resp.status}")
                return resp.status, raw, dict(resp.headers)
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as exc:
            last_error=exc
            if attempt+1<attempts:
                print(f"Health probe {attempt+1}/{attempts} did not wake service; retry in 15s")
                time.sleep(15)
                continue
            if isinstance(exc,urllib.error.HTTPError):
                content=exc.read().decode("utf-8",errors="replace")[:500]
                raise RuntimeError(f"{method} {path}: HTTP {exc.code}: {content}") from exc
            raise RuntimeError(f"{method} {path}: {exc}") from exc
    raise RuntimeError(f"{method} {path}: {last_error}")

def json_get(path):
    return json.loads(request(path)[1])

def png_fixture(width=160,height=120):
    def chunk(name, payload):
        return struct.pack(">I",len(payload))+name+payload+struct.pack(">I",zlib.crc32(name+payload)&0xffffffff)
    rows=b"".join(b"\x00"+bytes((30,150,100))*width for _ in range(height))
    ihdr=struct.pack(">IIBBBBB",width,height,8,2,0,0,0)
    return b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",ihdr)+chunk(b"IDAT",zlib.compress(rows))+chunk(b"IEND",b"")

def main():
    global BASE
    BASE=os.environ["BACKEND_URL"].rstrip("/")
    if not BASE.startswith("https://"):
        raise SystemExit("BACKEND_URL must be HTTPS")
    health=json_get("/health")
    assert health.get("status")=="ok", health
    providers=json_get("/api/providers")
    assert providers.get("providers"), providers
    market=json_get("/api/market/candles?timeframe=1h&period=5d")
    assert market.get("candles"), "Market endpoint returned no candles"
    analysis=json_get("/api/analysis?timeframe=1h&period=3mo")
    assert analysis.get("signal") in {"BUY","SELL","WAIT"}, analysis
    assert analysis.get("price",0)>0 and analysis.get("source"), analysis
    history=json_get("/api/signals")
    assert history.get("signals"), "Analysis signal was not persisted in history"
    risk_body=json.dumps({"entry":2000,"stop_loss":1990,"take_profit":2020,"account_balance":10000,"risk_percent":1,"contract_size":1}).encode()
    risk=json.loads(request("/api/risk/calculate","POST",risk_body,{"Content-Type":"application/json"})[1])
    assert abs(risk["risk_reward"]-2.0)<1e-9 and risk["position_units"]>0, risk
    boundary="----GoldLensSmoke"+uuid.uuid4().hex
    image=png_fixture()
    body=(f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"smoke.png\"\r\nContent-Type: image/png\r\n\r\n".encode()+image+f"\r\n--{boundary}--\r\n".encode())
    screenshot=json.loads(request("/api/screenshot/analyze","POST",body,{"Content-Type":f"multipart/form-data; boundary={boundary}"})[1])
    assert screenshot.get("signal")=="WAIT" and screenshot.get("visual_summary",{}).get("width")==160, screenshot
    _,_,cors=request("/api/providers","OPTIONS",headers={"Origin":"https://app.example","Access-Control-Request-Method":"GET"})
    cors={k.lower():v for k,v in cors.items()}
    assert cors.get("access-control-allow-origin")=="*", cors
    print("Live API smoke suite passed (market provider response was non-empty; screenshot is validated color-sampling only).")

if __name__ == "__main__":
    main()
