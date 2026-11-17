from __future__ import annotations
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Iterable
import pandas as pd
import numpy as np

@dataclass(frozen=True)
class CandleSet:
    symbol: str
    timeframe: str
    source: str
    fetched_at: str
    candles: list[dict]


def validate_candles(frame: pd.DataFrame) -> pd.DataFrame:
    """Normalize and validate OHLCV records. Raises ValueError on invalid data."""
    required = {"timestamp", "open", "high", "low", "close"}
    missing = required - set(frame.columns)
    if missing:
        raise ValueError(f"Missing required columns: {', '.join(sorted(missing))}")
    data = frame.copy()
    data["timestamp"] = pd.to_datetime(data["timestamp"], utc=True, errors="coerce")
    for column in ("open", "high", "low", "close"):
        data[column] = pd.to_numeric(data[column], errors="coerce")
    if "volume" not in data:
        data["volume"] = 0.0
    data["volume"] = pd.to_numeric(data["volume"], errors="coerce").fillna(0.0)
    data = data.replace([np.inf, -np.inf], np.nan)
    data = data.dropna(subset=["timestamp", "open", "high", "low", "close", "volume"])
    data = data[(data[["open", "high", "low", "close"]] > 0).all(axis=1)]
    data = data[data.volume >= 0]
    valid = (data.high >= data[["open", "close", "low"]].max(axis=1)) & (data.low <= data[["open", "close", "high"]].min(axis=1))
    data = data[valid].sort_values("timestamp").drop_duplicates("timestamp", keep="last")
    if data.empty:
        raise ValueError("No valid OHLC candles remain after validation")
    return data.reset_index(drop=True)


def serialize_candles(frame: pd.DataFrame) -> list[dict]:
    rows = []
    for row in frame.itertuples(index=False):
        rows.append({"time": int(row.timestamp.timestamp()), "timestamp": row.timestamp.isoformat(),
                     "open": float(row.open), "high": float(row.high), "low": float(row.low),
                     "close": float(row.close), "volume": float(row.volume)})
    return rows


def resample_candles(frame: pd.DataFrame, rule: str) -> pd.DataFrame:
    data = frame.set_index("timestamp")
    out = data.resample(rule, label="left", closed="left").agg({
        "open": "first", "high": "max", "low": "min", "close": "last", "volume": "sum"
    }).dropna(subset=["open", "high", "low", "close"]).reset_index()
    return validate_candles(out)
