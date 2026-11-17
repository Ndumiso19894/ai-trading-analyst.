from signals.analysis import analyze, apply_mtf_gate
from backtesting.engine import run_backtest

def test_analyzer_wait_or_direction_never_invents_price(candles):
    result=analyze(candles,"1h")
    assert result["signal"] in {"BUY","SELL","WAIT"}
    assert result["price"]==float(candles.close.iloc[-1])
    assert "reason" in result

def test_higher_timeframe_conflict_and_missing_context_force_wait():
    base={"signal":"BUY","entry":2000,"stop_loss":1990,"take_profit_1":2010,"take_profit_2":2020,"risk_reward":1,"conflicts":[]}
    conflicted=apply_mtf_gate(dict(base),{"4h":"SELL","1d":"BUY"},["4h","1d"])
    assert conflicted["signal"]=="WAIT" and conflicted["entry"] is None
    missing=apply_mtf_gate(dict(base),{"4h":"BUY"},["4h","1d"],["daily source offline"])
    assert missing["signal"]=="WAIT" and missing["mtf_status"]=="partial"

def test_backtest_metrics(candles):
    out=run_backtest(candles,warmup=220)
    assert out["total_trades"]>=0
    assert out["total_trades"]==out["winning_trades"]+out["losing_trades"]
    assert out["cost_model"]["deduction_per_round_trip"]>0
    assert "caveat" in out
