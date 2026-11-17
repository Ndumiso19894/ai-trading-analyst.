# GoldLens — AI Trading Analyst

Free-first XAU/USD research and analysis workspace. This codebase includes a working
FastAPI backend, React/TypeScript dashboard, deterministic indicator/signal/backtest
engines, CSV ingestion, SQLite signal log, and a native Android source project.

> **Not financial advice.** Data may be delayed or unavailable; calculations are
> heuristic. Signals and backtests do not guarantee fills or future performance.
> Validate all prices and contract details with your broker before any decision.

## Current capabilities

- Yahoo Finance chart endpoint integration (`XAUUSD=X`) — free and keyless but unofficial,
  may be delayed/rate-limited/changed; no real-time or SLA guarantee. Availability in a
  deployment depends on network access. Environment network restrictions may block it.
- CSV OHLCV import and validation; 60-second bounded in-memory provider candle cache.
- Interactive TradingView Lightweight Charts candlestick chart.
- EMA 20/50/100/200, RSI(14), MACD(12/26/9), ATR(14), Bollinger Bands and Stochastic.
- Heuristic swing structure and pivot-cluster support/resistance.
- Evidence-based BUY/SELL/WAIT with ATR stops and targets; multi-timeframe comparison.
- Position/risk calculator (user-supplied contract multiplier).
- CSV backtest (EMA cross; next-bar-open execution, spread/slippage assumptions,
  chronological 70/30 split, stop-first intrabar collision rule).
- SQLite signal log and best-effort outcome refresh from later provider bars at the signal timeframe.
- Screenshot upload is **image validation/color sampling only** and deliberately always
  returns WAIT. It does not recognize chart structure or claim true screenshot analysis.
- Kotlin/Jetpack Compose Android client source for backend analysis, market, history and
  image upload, with a GitHub Actions build workflow. Local toolchain downloads are blocked
  in this sandbox; consult workflow results for build/APK status.

## Run locally

Requirements: Python 3.11+, Node.js 20+, and for Android separately JDK 17 + Android SDK
(API 35) + Gradle 8.9-compatible installation.

### Backend

```bash
cd backend
python -m venv .venv
# Linux/macOS: source .venv/bin/activate; Windows: .venv\\Scripts\\activate
pip install -r requirements.txt
cp ../.env.example ../.env   # optional; keep secrets out of Git
python run.py                # binds 0.0.0.0:8000; Swagger at /docs
```

### Frontend (second terminal)

```bash
cd frontend
npm ci
npm run dev                  # http://localhost:5173
```

Vite proxies `/api` and `/health` to the backend. The browser must not call localhost
from a remote preview. Current provider API calls originate from the backend.

### Tests / production build

```bash
cd backend && python -m pytest -q
cd ../frontend && npm ci && npm run build
```

### Android source

```bash
cd android
# With JDK 17, Android SDK 35, and Gradle 8.9 installed:
gradle assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

Emulator backend URL defaults to `http://10.0.2.2:8000`. For a physical device, set
Backend URL to an address reachable on the local network. Cleartext HTTP is enabled for
local development only; use HTTPS before any public deployment.

## CSV format

UTF-8 CSV with columns `timestamp,open,high,low,close` and optional `volume`.
Timestamps are interpreted as UTC when timezone is absent. Rows are normalized, invalid
OHLC records dropped, duplicates resolved by keeping the last timestamp record. Market
CSV uploads are processed in memory and not retained. Backtest requires at least 440 bars.

## Data-source caveat

Provider details are exposed at `/api/providers`. Yahoo Finance's chart endpoint is an
unofficial endpoint and has no product SLA or guaranteed real-time XAU/USD quote. Request
limits, retention, terms and ticker availability can change; review the provider's current
terms before redistribution or commercial use. No API key is required. Never assume this
is an executable broker price. If access is unavailable, use downloaded broker/provider
CSV you are authorized to use; the analyzer works offline with CSV.

## Documentation

- `DEVELOPMENT_ROADMAP.md` — status and remaining scope
- `documentation/ARCHITECTURE.md` — components and design constraints
- `documentation/TESTING.md` — executed and planned verification
- `documentation/CHANGELOG.md` — milestones
- `android/README.md` — native build notes
