import numpy as np
import pandas as pd
import pytest

@pytest.fixture
def candles():
    n=320; x=np.arange(n); close=1900+0.25*x+8*np.sin(x/7)
    return pd.DataFrame({"timestamp":pd.date_range("2024-01-01",periods=n,freq="h",tz="UTC"),"open":close-0.2,"high":close+1,"low":close-1,"close":close,"volume":np.ones(n)*100})
