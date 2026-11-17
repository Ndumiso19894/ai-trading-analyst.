from __future__ import annotations
import pandas as pd
from indicators.calculations import calculate_indicators


def run_backtest(frame: pd.DataFrame, timeframe: str="1h", spread: float=0.30, slippage: float=0.10, fee: float=0.0, warmup: int=220, evaluation_start: int=0) -> dict:
    """Causal EMA 20/50 cross strategy. Entry on next bar open; stop checked before target."""
    d=calculate_indicators(frame).reset_index(drop=True)
    trades=[]; position=None
    costs=max(0.0,float(spread))/2 + max(0.0,float(slippage)) + max(0.0,float(fee))
    for i in range(max(warmup, evaluation_start, 1),len(d)):
        row=d.iloc[i]; prev=d.iloc[i-1]
        if position:
            outcome=None; exit_price=None
            # Conservative ambiguous-bar rule: assume stop hit first.
            if position["side"]=="long":
                if row.low <= position["stop"]: outcome="loss"; exit_price=position["stop"]
                elif row.high >= position["target"]: outcome="win"; exit_price=position["target"]
            else:
                if row.high >= position["stop"]: outcome="loss"; exit_price=position["stop"]
                elif row.low <= position["target"]: outcome="win"; exit_price=position["target"]
            if outcome:
                gross=(exit_price-position["entry"]) * (1 if position["side"]=="long" else -1)
                net=gross-costs*2
                trades.append({"entry_time":position["time"],"exit_time":row.timestamp.isoformat(),"side":position["side"],"entry":round(position["entry"],5),"exit":round(exit_price,5),"net_pnl_per_unit":round(net,5),"r_multiple":round(net/position["risk"],4),"outcome":"win" if net>0 else "loss"})
                position=None
        # Signal is confirmed on the previous completed bar; execute at this bar's open.
        if position is None and i >= 2 and pd.notna(d.iloc[i-2].ema_20) and pd.notna(d.iloc[i-2].ema_50) and pd.notna(prev.ema_20) and pd.notna(prev.ema_50):
            cross_up=d.iloc[i-2].ema_20<=d.iloc[i-2].ema_50 and prev.ema_20>d.iloc[i-2].ema_50
            cross_down=d.iloc[i-2].ema_20>=d.iloc[i-2].ema_50 and prev.ema_20<d.iloc[i-2].ema_50
            atr=float(prev.atr_14) if pd.notna(prev.atr_14) else 0
            if atr>0 and (cross_up or cross_down):
                side="long" if cross_up else "short"; entry=float(row.open)
                risk=1.5*atr
                position={"side":side,"entry":entry,"stop":entry-risk if side=="long" else entry+risk,"target":entry+3*atr if side=="long" else entry-3*atr,"risk":risk,"time":row.timestamp.isoformat()}
    # Open position intentionally excluded; avoids unrealized result being called a closed trade.
    pnls=[t["net_pnl_per_unit"] for t in trades]; wins=[p for p in pnls if p>0]; losses=[p for p in pnls if p<=0]
    equity=[]; current=peak=0.0; max_dd=0.0
    for p in pnls:
        current+=p; peak=max(peak,current); max_dd=max(max_dd,peak-current); equity.append(round(current,5))
    gross_profit=sum(wins); gross_loss=abs(sum(losses))
    win_streak=loss_streak=best_win=best_loss=0
    for trade in trades:
        if trade["outcome"]=="win": win_streak+=1; loss_streak=0
        else: loss_streak+=1; win_streak=0
        best_win=max(best_win,win_streak); best_loss=max(best_loss,loss_streak)
    return {"evaluation_start_index":max(warmup,evaluation_start,1),"evaluated_bars":max(0,len(d)-max(warmup,evaluation_start,1)),"max_consecutive_wins":best_win,"max_consecutive_losses":best_loss,"strategy":"EMA 20/50 cross, ATR 1.5 stop / 3 ATR target","timeframe":timeframe,"bars":len(d),"total_trades":len(trades),"open_trade_excluded":position is not None,"winning_trades":len(wins),"losing_trades":len(losses),"win_rate":round(len(wins)/len(trades)*100,2) if trades else None,"average_win":round(sum(wins)/len(wins),5) if wins else None,"average_loss":round(sum(losses)/len(losses),5) if losses else None,"net_return_per_unit":round(sum(pnls),5),"profit_factor":round(gross_profit/gross_loss,4) if gross_loss else (None if not gross_profit else "infinite"),"max_drawdown_per_unit":round(max_dd,5),"average_r":round(sum(t["r_multiple"] for t in trades)/len(trades),4) if trades else None,"cost_model":{"spread":spread,"slippage":slippage,"fee":fee,"deduction_per_round_trip":round(costs*2,5)},"trades":trades,"equity_curve":equity,"caveat":"Educational historical simulation, not a prediction. Unit P&L depends on contract specification. Open trades omitted; spread/slippage are user assumptions."}
