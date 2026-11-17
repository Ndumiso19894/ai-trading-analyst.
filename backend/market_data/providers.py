from __future__ import annotations
from datetime import datetime, timezone
import httpx
import pandas as pd

# Yahoo Finance's public chart endpoint requires no API key. It is unofficial,
# can be rate-limited/changed, offers delayed/non-exchange data, and has no SLA.
# We make its provenance visible and never describe it as guaranteed real-time.
INTERVALS = {"1m":"1m", "5m":"5m", "15m":"15m", "30m":"30m", "1h":"60m", "4h":"60m", "1d":"1d"}

class MarketDataError(RuntimeError):
    pass

def effective_range(timeframe: str, period: str) -> str:
    ranks={"1d":1,"5d":2,"1mo":3,"3mo":4,"6mo":5,"1y":6,"2y":7,"5y":8,"max":9}
    if timeframe=="1m" and ranks.get(period,9)>2: return "5d"
    if timeframe in {"5m","15m","30m"} and ranks.get(period,9)>3: return "1mo"
    if timeframe in {"1h","4h"} and ranks.get(period,9)>7: return "2y"
    return period

async def yahoo_chart(symbol: str = "XAUUSD=X", timeframe: str = "1h", period: str = "1mo") -> pd.DataFrame:
    if timeframe not in INTERVALS:
        raise MarketDataError(f"Unsupported timeframe: {timeframe}")
    # Yahoo limits intraday history; do not imply requested periods are guaranteed.
    # Intraday history is much shorter than daily history at this free endpoint.
    # Clamp rather than making requests that routinely fail for long intervals.
    effective_period=effective_range(timeframe,period)
    url = f"https://query1.finance.yahoo.com/v8/finance/chart/{symbol}"
    params = {"interval": INTERVALS[timeframe], "range": effective_period, "includePrePost": "false"}
    try:
        async with httpx.AsyncClient(timeout=12, headers={"User-Agent":"Mozilla/5.0 AITradingAnalyst/0.1"}) as client:
            response = await client.get(url, params=params)
            response.raise_for_status()
        payload = response.json()["chart"]
        if payload.get("error") or not payload.get("result"):
            raise MarketDataError(str(payload.get("error") or "Provider returned no chart data"))
        result = payload["result"][0]
        timestamps = result.get("timestamp") or []
        quotes = result["indicators"]["quote"][0]
        frame = pd.DataFrame({"timestamp": pd.to_datetime(timestamps, unit="s", utc=True),
            "open": quotes.get("open"), "high": quotes.get("high"), "low": quotes.get("low"),
            "close": quotes.get("close"), "volume": quotes.get("volume")})
        frame.attrs["requested_period"]=period
        frame.attrs["effective_period"]=effective_period
        return frame
    except (httpx.HTTPError, ValueError, KeyError, IndexError) as exc:
        raise MarketDataError(f"Yahoo Finance request failed: {exc}") from exc

PROVIDER_INFO = {
    "id":"yahoo_chart", "label":"Yahoo Finance chart endpoint", "access":"Free, no key",
    "reliability":"Unofficial endpoint; may change or rate-limit; no SLA",
    "freshness":"May be delayed; not guaranteed real-time", "license":"Yahoo terms apply; verify permitted use",
    "history":"Provider-dependent (intraday limits; daily history generally longer)",
}
