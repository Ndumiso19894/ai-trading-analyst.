import json, os, sqlite3
from pathlib import Path

DB_PATH = os.getenv("SIGNAL_DB_PATH", str(Path(__file__).resolve().parents[1] / "trading.sqlite3"))

def connect():
    conn=sqlite3.connect(DB_PATH)
    conn.execute("CREATE TABLE IF NOT EXISTS signals (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at TEXT NOT NULL, instrument TEXT NOT NULL, timeframe TEXT NOT NULL, direction TEXT NOT NULL, price REAL, payload TEXT NOT NULL, outcome TEXT NOT NULL DEFAULT 'unresolved')")
    return conn

def save_signal(signal: dict) -> int:
    conn=connect()
    # Anchor outcome tracking at the last observed market candle, not analysis wall-clock time.
    direction=signal.get("signal","WAIT")
    outcome="not_applicable" if direction=="WAIT" else "unresolved"
    cur=conn.execute("INSERT INTO signals(created_at,instrument,timeframe,direction,price,payload,outcome) VALUES(?,?,?,?,?,?,?)", (signal.get("data_timestamp") or signal.get("as_of",""),signal.get("instrument","XAU/USD"),signal.get("timeframe","1h"),direction,signal.get("price"),json.dumps(signal),outcome))
    conn.commit(); ident=cur.lastrowid; conn.close(); return ident

def update_outcome(signal_id: int, outcome: str):
    conn=connect(); conn.execute("UPDATE signals SET outcome=? WHERE id=?", (outcome,signal_id)); conn.commit(); conn.close()

def performance_summary():
    conn=connect(); rows=conn.execute("SELECT outcome,COUNT(*) FROM signals GROUP BY outcome").fetchall(); conn.close()
    counts={k:int(v) for k,v in rows}; wins=counts.get("take_profit_1",0); losses=counts.get("stop_loss",0); resolved=wins+losses
    return {"counts":counts,"resolved_directional_signals":resolved,"win_rate_percent":round(wins/resolved*100,2) if resolved else None,"caveat":"Outcome counts only; historical provider bars may be delayed and do not model broker costs, slippage, or actual fills. WAIT records are excluded from win-rate denominator."}

def list_open_signals():
    conn=connect(); rows=conn.execute("SELECT id,created_at,direction,payload FROM signals WHERE outcome='unresolved'").fetchall(); conn.close()
    return [{"id":r[0],"created_at":r[1],"direction":r[2],"analysis":json.loads(r[3])} for r in rows]

def list_signals(limit: int=100):
    conn=connect(); rows=conn.execute("SELECT id,created_at,instrument,timeframe,direction,price,payload,outcome FROM signals ORDER BY id DESC LIMIT ?", (max(1,min(limit,500)),)).fetchall(); conn.close()
    return [{"id":r[0],"created_at":r[1],"instrument":r[2],"timeframe":r[3],"direction":r[4],"price":r[5],"analysis":json.loads(r[6]),"outcome":r[7]} for r in rows]
