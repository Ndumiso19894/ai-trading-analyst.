from __future__ import annotations
from datetime import datetime, timezone
import pandas as pd
from indicators.calculations import calculate_indicators


def apply_mtf_gate(result: dict, higher: dict[str,str], required: list[str], errors: list[str]|None=None) -> dict:
    """Block directional live signals when a major timeframe conflicts or is unavailable."""
    errors=errors or []
    opposing=any(direction in {"BUY","SELL"} and direction!=result.get("signal") for direction in higher.values())
    missing=len(higher)<len(required)
    result["higher_timeframes"]=higher
    result["mtf_status"]="checked" if not missing and not errors else "partial" if higher else "unavailable"
    if errors: result["context_errors"]=errors
    if result.get("signal") in {"BUY","SELL"} and (opposing or missing):
        result["unfiltered_signal"]=result["signal"]
        result["signal"]="WAIT"
        result["entry"]=result["stop_loss"]=result["take_profit_1"]=result["take_profit_2"]=result["risk_reward"]=None
        result.setdefault("conflicts",[]).append(f"Higher-timeframe conflict: {higher}" if opposing else "Higher timeframes unavailable; confirmation could not be verified.")
        result["reason"]="WAIT: higher-timeframe alignment is conflicting or unavailable; no trade levels are issued."
    return result


def levels(frame: pd.DataFrame, lookback: int = 120, tolerance_atr: float = 0.35) -> dict:
    d = frame.tail(lookback)
    if len(d) < 10:
        return {"support": [], "resistance": []}
    highs, lows = d.high.to_numpy(), d.low.to_numpy()
    pivots = []
    for i in range(2, len(d)-2):
        if highs[i] >= max(highs[i-2:i+3]): pivots.append((float(highs[i]), "resistance"))
        if lows[i] <= min(lows[i-2:i+3]): pivots.append((float(lows[i]), "support"))
    close = float(d.close.iloc[-1]); atr_s = calculate_indicators(d).atr_14.iloc[-1]
    tol = float(atr_s)*tolerance_atr if pd.notna(atr_s) else close*0.002
    result = {"support": [], "resistance": []}
    for side in result:
        candidates = sorted([p for p,t in pivots if t == side], key=lambda x: abs(x-close))
        clusters = []
        for value in candidates:
            cluster = next((c for c in clusters if abs(c[0]-value) <= tol), None)
            if cluster: cluster[0] = (cluster[0]*cluster[1]+value)/(cluster[1]+1); cluster[1]+=1
            else: clusters.append([value, 1])
        result[side] = [{"price":round(c[0], 4), "touches":c[1], "strength":"strong" if c[1]>=3 else "moderate" if c[1]==2 else "weak"} for c in clusters[:5]]
    return result


def structure(frame: pd.DataFrame) -> dict:
    d = frame.tail(80)
    highs=[]; lows=[]
    for i in range(2, len(d)-2):
        if d.high.iloc[i] >= d.high.iloc[i-2:i+3].max(): highs.append(float(d.high.iloc[i]))
        if d.low.iloc[i] <= d.low.iloc[i-2:i+3].min(): lows.append(float(d.low.iloc[i]))
    h,l=highs[-2:],lows[-2:]
    if len(h)==2 and len(l)==2:
        trend = "bullish structure" if h[1]>h[0] and l[1]>l[0] else "bearish structure" if h[1]<h[0] and l[1]<l[0] else "mixed structure"
    else: trend="insufficient swing points"
    return {"trend":trend,"swing_highs":h,"swing_lows":l}


def analyze(frame: pd.DataFrame, timeframe: str="1h") -> dict:
    d=calculate_indicators(frame)
    row=d.iloc[-1]; price=float(row.close)
    if len(d)<30: return {"signal":"WAIT","reason":"At least 30 candles are needed for a stable assessment.","price":price,"timeframe":timeframe,"indicators":{},"evidence":[],"conflicts":[],"levels":levels(frame),"structure":structure(frame)}
    evidence=[]; conflicts=[]; score=0
    if pd.notna(row.get("ema_20")) and pd.notna(row.get("ema_50")):
        if row.ema_20>row.ema_50 and price>row.ema_20: score+=1; evidence.append("Price is above EMA 20 and EMA 20 is above EMA 50")
        elif row.ema_20<row.ema_50 and price<row.ema_20: score-=1; evidence.append("Price is below EMA 20 and EMA 20 is below EMA 50")
        else: conflicts.append("Price and short-term EMA alignment is mixed")
    if pd.notna(row.get("rsi_14")):
        if 52<=row.rsi_14<=70: score+=1; evidence.append(f"RSI(14) is positive at {row.rsi_14:.1f}")
        elif 30<=row.rsi_14<=48: score-=1; evidence.append(f"RSI(14) is negative at {row.rsi_14:.1f}")
        elif row.rsi_14>70 or row.rsi_14<30: conflicts.append(f"RSI(14) is extreme at {row.rsi_14:.1f}; chase risk")
    if pd.notna(row.get("macd_histogram")):
        if row.macd_histogram>0: score+=1; evidence.append("MACD histogram is positive")
        elif row.macd_histogram<0: score-=1; evidence.append("MACD histogram is negative")
    s=structure(frame)
    if "bullish" in s["trend"]: score+=1; evidence.append("Recent swing structure is bullish")
    elif "bearish" in s["trend"]: score-=1; evidence.append("Recent swing structure is bearish")
    direction="BUY" if score>=3 else "SELL" if score<=-3 else "WAIT"
    atr=float(row.atr_14) if pd.notna(row.get("atr_14")) else None
    entry=price; stop=target1=target2=None; rr=None
    if direction!="WAIT" and atr and atr>0:
        stop=price-1.5*atr if direction=="BUY" else price+1.5*atr
        target1=price+1.5*atr if direction=="BUY" else price-1.5*atr
        target2=price+3*atr if direction=="BUY" else price-3*atr
        rr=1.0
    else: direction="WAIT"
    reason=("Evidence is mixed or insufficient; wait for stronger confirmation." if direction=="WAIT" else f"{direction} evidence aligns across trend, momentum and structure; ATR-based levels are indicative, not guaranteed.")
    return {"signal":direction,"instrument":"XAU/USD","timeframe":timeframe,"as_of":datetime.now(timezone.utc).isoformat(),"price":price,"entry":entry if direction!="WAIT" else None,"stop_loss":stop,"take_profit_1":target1,"take_profit_2":target2,"risk_reward":rr,"trend":s["trend"],"indicators":{k:(float(row[k]) if pd.notna(row.get(k)) else None) for k in ["ema_20","ema_50","ema_100","ema_200","rsi_14","macd","macd_signal","macd_histogram","atr_14","bb_upper","bb_middle","bb_lower","stoch_k","stoch_d"]},"evidence":evidence,"conflicts":conflicts,"levels":levels(frame),"structure":s,"reason":reason,"invalidation":"Close beyond the stop level; reassess after a new candle." if direction!="WAIT" else "No valid setup to invalidate."}
