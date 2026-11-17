# Testing

## Backend

```bash
cd backend
pip install -r requirements.txt
pytest -q
```

- `tests/test_health.py` — `/health` and `/` return 200 with expected payloads.
- Indicator tests (Phase 3) will verify EMA/RSI/MACD/ATR against hand-computed values.
- Backtest tests (Phase 6) will use fixed synthetic series with known outcomes.

## Frontend

```bash
cd frontend
npm install
npm run build   # tsc + vite build must pass
```

## Rules

- Never report a test as passing unless it was executed.
- Every engine ships with tests before its phase is marked complete.
