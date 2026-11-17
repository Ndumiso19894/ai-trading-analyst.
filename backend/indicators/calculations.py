from __future__ import annotations
import numpy as np
import pandas as pd


def calculate_indicators(frame: pd.DataFrame) -> pd.DataFrame:
    """Calculate common indicators with explicit warm-up NaNs."""
    d = frame.copy()
    close, high, low = d.close.astype(float), d.high.astype(float), d.low.astype(float)
    for period in (20, 50, 100, 200):
        d[f"ema_{period}"] = close.ewm(span=period, adjust=False, min_periods=period).mean()
    delta = close.diff()
    gain, loss = delta.clip(lower=0), -delta.clip(upper=0)
    avg_gain = gain.ewm(alpha=1/14, adjust=False, min_periods=14).mean()
    avg_loss = loss.ewm(alpha=1/14, adjust=False, min_periods=14).mean()
    rs = avg_gain / avg_loss.replace(0, np.nan)
    d["rsi_14"] = 100 - (100 / (1 + rs))
    d.loc[(avg_loss == 0) & (avg_gain > 0), "rsi_14"] = 100.0
    d.loc[(avg_gain == 0) & (avg_loss > 0), "rsi_14"] = 0.0
    ema12 = close.ewm(span=12, adjust=False, min_periods=12).mean()
    ema26 = close.ewm(span=26, adjust=False, min_periods=26).mean()
    d["macd"] = ema12 - ema26
    d["macd_signal"] = d.macd.ewm(span=9, adjust=False, min_periods=9).mean()
    d["macd_histogram"] = d.macd - d.macd_signal
    prev_close = close.shift(1)
    tr = pd.concat([high-low, (high-prev_close).abs(), (low-prev_close).abs()], axis=1).max(axis=1)
    d["atr_14"] = tr.ewm(alpha=1/14, adjust=False, min_periods=14).mean()
    mid = close.rolling(20, min_periods=20).mean()
    std = close.rolling(20, min_periods=20).std(ddof=0)
    d["bb_middle"], d["bb_upper"], d["bb_lower"] = mid, mid+2*std, mid-2*std
    lowest, highest = low.rolling(14, min_periods=14).min(), high.rolling(14, min_periods=14).max()
    denom = (highest-lowest).replace(0, np.nan)
    d["stoch_k"] = 100*(close-lowest)/denom
    d["stoch_d"] = d.stoch_k.rolling(3, min_periods=3).mean()
    return d
