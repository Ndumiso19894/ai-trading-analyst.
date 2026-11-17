import numpy as np
from indicators.calculations import calculate_indicators

def test_ema_known_constant(candles):
    d=candles.copy(); d["close"]=10.0
    out=calculate_indicators(d)
    assert out.ema_20.iloc[-1]==10
    assert out.ema_200.iloc[-1]==10

def test_rsi_monotonic_gain_and_atr_constant_range(candles):
    rising=candles.copy(); rising["close"]=np.arange(len(rising),dtype=float)+100
    rising["open"]=rising.close-.1; rising["high"]=rising.close+1; rising["low"]=rising.close-1
    out=calculate_indicators(rising)
    assert out.rsi_14.iloc[-1]==100.0
    flat=candles.copy(); flat["open"]=100; flat["close"]=100; flat["high"]=101; flat["low"]=99
    atr=calculate_indicators(flat).atr_14.iloc[-1]
    assert abs(atr-2.0)<1e-9

def test_indicator_columns_and_bounds(candles):
    out=calculate_indicators(candles)
    assert {"rsi_14","macd","macd_histogram","atr_14","bb_upper","bb_lower","stoch_k"} <= set(out)
    assert out.rsi_14.dropna().between(0,100).all()
    assert (out.atr_14.dropna()>0).all()
    assert (out.bb_upper.dropna()>=out.bb_lower.dropna()).all()
