# AI Trading Analyst

Free-first, AI-assisted trading analysis platform — initially specialising in **Gold (XAU/USD)**.

Two independent analysis modes:

- **Mode A — Automatic market analysis:** fetch OHLC data, compute indicators, multi-timeframe
  analysis, generate BUY / SELL / WAIT signals with entry, stop-loss, take-profit and reasoning.
- **Mode B — Screenshot analysis:** upload a chart screenshot (PNG/JPEG) and receive a
  visual assessment (direction, structure, support/resistance, patterns) with explicit
  uncertainty reporting — never inventing prices or indicator readings.

The platform must remain useful when external AI or data APIs hit free-tier limits:
a deterministic mathematical engine + explanation layer works fully offline.

> ⚠️ This is an analysis tool, not financial advice. Signals are probabilistic and
> backtest-dependent. Never trade with money you cannot afford to lose.

## Status

**Phase 1 — Project foundation (in progress).** Backend (FastAPI) and frontend (React + TS)
skeletons are in place with a working health check. Market data, indicators, signals,
screenshot analysis, backtesting and Android follow per `DEVELOPMENT_ROADMAP.md`.

## Quick start

### Backend

```bash
cd backend
python -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp ../.env.example ../.env   # optional
python run.py                # http://localhost:8000  (docs at /docs)
```

### Frontend

```bash
cd frontend
npm install
npm run dev                  # http://localhost:5173 (proxies /health to :8000)
```

### Tests

```bash
cd backend
pytest -q
```

## Repository layout

```
backend/        FastAPI app, indicators, strategies, signals, market data, backtesting, tests
frontend/       React + TypeScript dashboard (Vite)
android/        Kotlin / Jetpack Compose app (Phase 8)
documentation/  Architecture, changelog, testing notes
datasets/       Sample CSV data (git-ignored except samples)
scripts/        Utility scripts
```

See `documentation/ARCHITECTURE.md` for design decisions and `DEVELOPMENT_ROADMAP.md`
for the phased plan.

## Zero-cost policy

Only free / open-source technology. No paid APIs, feeds, or AI services. Secrets live in
`.env` (never committed). If a feature cannot be delivered free, the roadmap documents
the free alternative instead.

## GitHub

Private repository on GitHub Free. See `documentation/` for continuity notes so any
environment or agent can resume work from this repo alone.
