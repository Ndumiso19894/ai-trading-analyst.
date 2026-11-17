from __future__ import annotations
from datetime import datetime, timezone
import io
import pandas as pd
from fastapi import APIRouter, File, HTTPException, Query, UploadFile
from pydantic import BaseModel, Field
from market_data.models import validate_candles, serialize_candles, resample_candles
from market_data.providers import yahoo_chart, MarketDataError, PROVIDER_INFO
from market_data.cache import cache
from indicators.calculations import calculate_indicators
from signals.analysis import analyze, apply_mtf_gate
from signals.storage import save_signal, list_signals, list_open_signals, update_outcome, performance_summary
from backtesting.engine import run_backtest

router=APIRouter(prefix="/api")
TIMEFRAMES={"1m":"1min","5m":"5min","15m":"15min","30m":"30min","1h":"1h","4h":"4h","1d":"1D"}

async def get_cached(symbol: str, timeframe: str, period: str):
    key=(symbol,timeframe,period)
    hit=cache.get(key)
    if hit:return hit[0],hit[1],True
    raw_frame=await yahoo_chart(symbol,timeframe,period)
    requested=raw_frame.attrs.get("requested_period",period); effective=raw_frame.attrs.get("effective_period",period)
    frame=validate_candles(raw_frame)
    note=None if requested==effective else f"Intraday provider retention capped requested {requested} history to {effective}."
    meta={"source":PROVIDER_INFO["label"],"fetched_at":datetime.now(timezone.utc).isoformat(),"delayed":True,"requested_period":requested,"effective_period":effective,"history_note":note}
    cache.put(key,frame,meta)
    return frame,meta,False

@router.get("/providers")
def providers(): return {"providers":[PROVIDER_INFO],"note":"Free provider service and terms can change; values may be delayed and are not guaranteed executable quotes."}

@router.post("/market/csv")
async def upload_csv(file: UploadFile=File(...), timeframe: str=Query("1h")):
    if not file.filename or not file.filename.lower().endswith(".csv"): raise HTTPException(400,"Upload a .csv file")
    raw=await file.read()
    if len(raw)>10_000_000: raise HTTPException(413,"CSV exceeds 10 MB")
    try:
        if timeframe not in TIMEFRAMES: raise ValueError("Unsupported timeframe")
        frame=validate_candles(pd.read_csv(io.BytesIO(raw)))
    except Exception as exc: raise HTTPException(422,str(exc))
    return {"instrument":"XAU/USD","timeframe":timeframe,"source":f"CSV upload: {file.filename}","fetched_at":datetime.now(timezone.utc).isoformat(),"count":len(frame),"candles":serialize_candles(frame)}

@router.get("/market/candles")
async def candles(timeframe: str="1h", period: str="1mo", symbol: str="XAUUSD=X"):
    if timeframe not in TIMEFRAMES: raise HTTPException(400,"Timeframe must be one of: "+", ".join(TIMEFRAMES))
    if symbol != "XAUUSD=X": raise HTTPException(400,"Only the configured XAU/USD symbol is currently supported")
    if period not in {"1d","5d","1mo","3mo","6mo","1y","2y","5y","max"}: raise HTTPException(400,"Unsupported period")
    provider_tf=timeframe
    if timeframe=="4h": provider_tf="1h"
    try:
        frame,meta,cached=await get_cached(symbol,provider_tf,period)
        if timeframe=="4h": frame=resample_candles(frame,"4h")
    except (MarketDataError,ValueError) as exc: raise HTTPException(502,str(exc))
    return {"instrument":symbol,"timeframe":timeframe,**meta,"cached":cached,"candles":serialize_candles(frame)}

@router.get("/analysis")
async def analysis(timeframe: str="1h", period: str="3mo"):
    if timeframe not in TIMEFRAMES: raise HTTPException(400,"Unsupported timeframe")
    try:
        f,meta,cached=await get_cached("XAUUSD=X", "1h" if timeframe=="4h" else timeframe, period)
        if timeframe=="4h": f=resample_candles(f,"4h")
    except (MarketDataError,ValueError) as exc: raise HTTPException(502,str(exc))
    result=analyze(f,timeframe); result["source"]=meta["source"]; result["cached"]=cached; result["data_timestamp"]=f.timestamp.iloc[-1].isoformat()
    context_frames={"1m":["1h","1d"],"5m":["1h","1d"],"15m":["1h","1d"],"30m":["1h","1d"],"1h":["4h","1d"],"4h":["1d"]}.get(timeframe,[])
    higher={}; context_errors=[]
    for higher_tf in context_frames:
        try:
            hf,_,_=await get_cached("XAUUSD=X","1h" if higher_tf=="4h" else higher_tf,period)
            if higher_tf=="4h": hf=resample_candles(hf,"4h")
            higher[higher_tf]=analyze(hf,higher_tf)["signal"]
        except Exception as exc: context_errors.append(f"{higher_tf}: {exc}")
    result=apply_mtf_gate(result,higher,context_frames,context_errors)
    result["signal_id"]=save_signal(result)
    return result

@router.post("/analysis/csv")
async def analysis_csv(file: UploadFile=File(...), timeframe: str=Query("1h")):
    if not file.filename or not file.filename.lower().endswith(".csv"): raise HTTPException(400,"Upload a .csv file")
    raw=await file.read()
    if len(raw)>10_000_000: raise HTTPException(413,"CSV exceeds 10 MB")
    try:
        if timeframe not in TIMEFRAMES: raise ValueError("Unsupported timeframe")
        frame=validate_candles(pd.read_csv(io.BytesIO(raw)))
    except Exception as exc: raise HTTPException(422,str(exc))
    result=analyze(frame,timeframe); result["source"]=f"CSV upload: {file.filename}"; result["data_timestamp"]=frame.timestamp.iloc[-1].isoformat(); result["mtf_status"]="not checked: CSV contains one timeframe"; result["signal_id"]=save_signal(result)
    return result

@router.get("/analysis/multi-timeframe")
async def multi_timeframe():
    outputs={}
    for tf in ["1d","4h","1h","15m"]:
        try:
            f=validate_candles(await yahoo_chart("XAUUSD=X","1h" if tf=="4h" else tf,"3mo" if tf in ("1d","4h","1h") else "5d"))
            if tf=="4h": f=resample_candles(f,"4h")
            outputs[tf]=analyze(f,tf)
        except Exception as exc: outputs[tf]={"signal":"WAIT","error":str(exc)}
    directions=[x.get("signal") for x in outputs.values() if not x.get("error")]
    directional={x for x in directions if x in {"BUY","SELL"}}
    agreement="aligned" if directions and len(directional)==1 and all(x in directional for x in directions) else "conflicting" if len(directional)>1 else "no-confirmation" if directions else "unavailable"
    assessment="Strong timeframe disagreement; wait for alignment." if agreement=="conflicting" else "Directional signals align." if agreement=="aligned" else "At least one timeframe has no-confirmation; stand aside." if agreement=="no-confirmation" else "Insufficient provider data."
    return {"timeframes":outputs,"agreement":agreement,"assessment":assessment,"as_of":datetime.now(timezone.utc).isoformat()}

class RiskRequest(BaseModel):
    entry: float=Field(gt=0); stop_loss: float=Field(gt=0); take_profit: float=Field(gt=0); account_balance: float=Field(gt=0); risk_percent: float=Field(gt=0,le=5); contract_size: float=Field(default=1,gt=0)

@router.post("/risk/calculate")
def risk(req: RiskRequest):
    distance=abs(req.entry-req.stop_loss); target=abs(req.take_profit-req.entry)
    if distance==0: raise HTTPException(422,"Entry and stop-loss must differ")
    risk_money=req.account_balance*req.risk_percent/100
    units=risk_money/(distance*req.contract_size)
    return {"stop_distance":distance,"target_distance":target,"risk_reward":target/distance,"position_units":units,"risk_amount":risk_money,"potential_loss":risk_money,"potential_gain":units*target*req.contract_size,"warning":"Illustrative only. Configure contract size and account currency conversion for your broker; excludes slippage and fees. Not a trade recommendation."}

@router.post("/backtest")
async def backtest(file: UploadFile=File(...), timeframe: str=Query("1h"), spread: float=Query(.30,ge=0), slippage: float=Query(.10,ge=0)):
    if not file.filename or not file.filename.lower().endswith(".csv"): raise HTTPException(400,"Upload a .csv file")
    raw=await file.read()
    if len(raw)>20_000_000: raise HTTPException(413,"CSV exceeds 20 MB")
    try: frame=validate_candles(pd.read_csv(io.BytesIO(raw)))
    except Exception as exc: raise HTTPException(422,str(exc))
    if timeframe not in TIMEFRAMES: raise HTTPException(422,"Unsupported timeframe")
    if len(frame)<440: raise HTTPException(422,"At least 440 candles are required for indicator warm-up plus a meaningful 70/30 in-sample/out-of-sample split")
    split=int(len(frame)*0.7)
    full=run_backtest(frame,timeframe,spread,slippage)
    full["in_sample"]=run_backtest(frame.iloc[:split].reset_index(drop=True),timeframe,spread,slippage)
    full["out_of_sample"]=run_backtest(frame,timeframe,spread,slippage,evaluation_start=split)
    full["split"]={"method":"chronological 70/30; indicators use past bars only; test entries begin after split with historical warm-up context","index":split,"timestamp":frame.timestamp.iloc[split].isoformat()}
    return full

@router.get("/signals")
def signals(limit: int=100): return {"signals":list_signals(limit)}

@router.get("/signals/performance")
def signal_performance(): return performance_summary()

@router.post("/signals/refresh-outcomes")
async def refresh_outcomes():
    """Evaluate persisted directional signals against subsequent 1h bars; conservative stop-first collisions."""
    opened=list_open_signals(); evaluated=0
    if not opened:return {"evaluated":0,"message":"No unresolved signals."}
    frames={}
    for item in opened:
        a=item["analysis"]
        if a.get("signal") not in {"BUY","SELL"} or a.get("stop_loss") is None:continue
        tf=a.get("timeframe","1h")
        if tf not in frames:
            try:
                frame,_,_=await get_cached("XAUUSD=X","1h" if tf=="4h" else tf,"3mo")
                frames[tf]=resample_candles(frame,"4h") if tf=="4h" else frame
            except Exception as exc: raise HTTPException(502,f"Cannot refresh {tf} signal outcomes without market data: {exc}")
        since=pd.to_datetime(item["created_at"],utc=True,errors="coerce")
        bars=frames[tf][frames[tf].timestamp>since]
        for bar in bars.itertuples(index=False):
            if a["signal"]=="BUY":
                if bar.low<=a["stop_loss"]: update_outcome(item["id"],"stop_loss");evaluated+=1;break
                if a.get("take_profit_1") and bar.high>=a["take_profit_1"]: update_outcome(item["id"],"take_profit_1");evaluated+=1;break
            else:
                if bar.high>=a["stop_loss"]: update_outcome(item["id"],"stop_loss");evaluated+=1;break
                if a.get("take_profit_1") and bar.low<=a["take_profit_1"]: update_outcome(item["id"],"take_profit_1");evaluated+=1;break
        else:
            if len(bars)>=120: update_outcome(item["id"],"expired");evaluated+=1
    return {"evaluated":evaluated,"still_unresolved":len(opened)-evaluated,"caveat":"Historical bars may omit broker spreads; both stop and target in one bar resolves as stop-first."}

@router.post("/screenshot/analyze")
async def screenshot(file: UploadFile=File(...)):
    if file.content_type not in {"image/png","image/jpeg"}: raise HTTPException(415,"Use PNG or JPEG image")
    raw=await file.read()
    if len(raw)>8_000_000: raise HTTPException(413,"Image exceeds 8 MB")
    try:
        from PIL import Image
        image=Image.open(io.BytesIO(raw))
        if image.width<100 or image.height<80: raise ValueError("Image is too small to assess")
        if image.width*image.height>20_000_000: raise ValueError("Image exceeds 20 megapixels")
        image=image.convert("RGB")
        image.thumbnail((800,800)); pixels=list(image.getdata()); total=len(pixels)
        bullish=sum(1 for r,g,b in pixels if g>r*1.18 and g>b*0.9 and g>70)
        bearish=sum(1 for r,g,b in pixels if r>g*1.25 and r>b*1.1 and r>85)
        # Color-count heuristics are not candlestick recognition; abstain from direction.
        return {"signal":"WAIT","assessment":"Image accepted and visually sampled. The local color heuristic cannot reliably distinguish candlesticks from indicators, UI colors, or chart themes, so no directional assessment is made.","visual_summary":{"width":image.width,"height":image.height,"greenish_pixels":bullish,"reddish_pixels":bearish,"sampled_pixels":total},"bullish_evidence":[],"bearish_evidence":[],"potential_entry_area":None,"invalidation":None,"targets":[],"missing_information":["Instrument and timeframe cannot be safely verified","Price scale and exact prices are not extracted","Chart structure and indicators are not model-recognized"],"method":"Local Pillow image validation and color sampling; no external AI or OCR"}
    except Exception as exc: raise HTTPException(422,f"Image could not be decoded: {exc}")
