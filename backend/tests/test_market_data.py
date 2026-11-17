import pandas as pd
import pytest
from market_data.models import validate_candles, resample_candles
from market_data.providers import effective_range

def test_validation_deduplicates_and_sorts():
    f=pd.DataFrame({"timestamp":["2024-01-02","2024-01-01","2024-01-01"],"open":[2,1,1],"high":[3,2,2],"low":[1,0.5,.5],"close":[2.5,1.5,1.6]})
    out=validate_candles(f)
    assert len(out)==2 and out.timestamp.is_monotonic_increasing and out.iloc[0].close==1.6

def test_invalid_ohlc_removed():
    f=pd.DataFrame({"timestamp":["2024-01-01"],"open":[5],"high":[4],"low":[1],"close":[3]})
    with pytest.raises(ValueError): validate_candles(f)

def test_hourly_aggregation(candles):
    two=candles.iloc[:2].copy()
    out=resample_candles(two,"2h")
    assert len(out)==1 and out.iloc[0].high==two.high.max() and out.iloc[0].low==two.low.min()

def test_intraday_history_is_capped_to_documented_safe_ranges():
    assert effective_range("1m","3mo")=="5d"
    assert effective_range("15m","1y")=="1mo"
    assert effective_range("1h","max")=="2y"
    assert effective_range("1d","max")=="max"
