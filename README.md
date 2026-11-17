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
  image upload. CI successfully built a signed-debug APK and an unsigned release APK. The
  [Actions run and APK artifact](https://github.com/Ndumiso19894/ai-trading-analyst./actions/runs/44065622695)
  is available for 30 days; a release keystore is owner-supplied.

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

The app defaults to the Render Blueprint HTTPS host and checks `/health` on launch. That
hostname will not respond until the owner deploys the Blueprint. In the app, **Advanced
connection settings** permits a different HTTPS URL and saves it on-device. HTTP is allowed
only in debug builds for local development.

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

## Phone-first backend deployment

A free Render Blueprint is in `render.yaml`. Render account/GitHub authorization is required
before a live service exists; see `documentation/DEPLOYMENT.md` for the phone workflow, free
sleep/storage limits, and verification checklist. The Android app is preconfigured for the
Blueprint's expected HTTPS host and retains an advanced, persistent endpoint override. That
host is a target, not a verified public API until the owner completes the Render deployment.

## Documentation

- `DEVELOPMENT_ROADMAP.md` — status and remaining scope
- `documentation/ARCHITECTURE.md` — components and design constraints
- `documentation/TESTING.md` — executed and planned verification
- `documentation/CHANGELOG.md` — milestones
- `android/README.md` — native build notes
