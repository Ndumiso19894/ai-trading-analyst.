# GoldLens — Trading Analyst

GoldLens is the existing Android-first analysis project with a native Kotlin/Compose app, FastAPI reference/backend, and React/TypeScript dashboard. Its local Android analysis does not require a hosted API. No market prices or signals are fabricated.

> **Not financial advice.** Analysis is heuristic, broker specifications vary, and neither signals nor historical simulations guarantee fills or future performance.

## Current functionality

### Android

- Offline Kotlin indicators, market-structure summary, pivot-cluster support/resistance and explainable BUY/SELL/WAIT over user-imported OHLC.
- Room persistence for local candle sets, saved analyses/zones, manual journal and settings.
- CSV import from Android's document picker and CSV export of the selected candle set. Import currently loads after validation; it does not offer a preview.
- Instrument selector/catalog covering EUR/USD, GBP/USD, USD/JPY, USD/CHF, AUD/USD, NZD/USD, USD/CAD, EUR/GBP, EUR/JPY, GBP/JPY, USD/ZAR, XAU/USD, XAG/USD and XPT/USD. Broker contract/tick/volume details remain unset until verified and entered.
- Offline risk calculator requiring user-supplied broker contract size or tick specifications; account currency conversion is not implemented.
- User-started, draggable/expandable overlay analyzing locally saved candles, with explicit overlay permission and foreground notification.
- GoldLens launcher icon using Android adaptive and legacy launcher resources.

### Backend and web dashboard

- FastAPI endpoints for OHLC CSV, indicators, analysis, multi-timeframe analysis, risk, historical CSV backtest, signal history and screenshot upload.
- React/TypeScript dashboard with charts, provider data actions, analysis, calculator and backend backtesting.
- The only configured external market-data adapter is Yahoo Finance's unofficial chart endpoint, fixed to XAU/USD. It is free/keyless to call but may be delayed, rate-limited or changed, has no SLA, and is not guaranteed real-time. Coverage of the Android instrument catalog is not verified. Use only where its current terms permit; CSV is the reliable user-controlled offline route.
- Screenshot endpoint does bounded image validation and rough color sampling, then abstains with WAIT. It does not perform chart/OHLC recognition.

## Known not implemented

- Android external market-data integration, source-mode comparison, bid/ask/spread, or validated coverage across the registry.
- Android MediaProjection capture, crop/preview, genuine screenshot chart recognition, and desktop MT5 bridge.
- Kotlin/Android backtesting UI/engine (the CSV backtester exists in FastAPI only).
- Android WorkManager alerts, Google Drive OAuth/backup, GitHub data snapshots and unified sync manager.
- Android multi-timeframe analysis from separate datasets, Room migrations, CSV preview, and exports of journals/signals/backtests/zones.
- Physical-device or emulator acceptance testing.

## Run locally

Requirements: Python 3.11+, Node.js 20+, and (for Android) JDK 17, Android SDK API 35, Build Tools 35.0.0 and Gradle 8.9.

### Backend

```bash
cd backend
python -m venv .venv
# activate the environment, then:
pip install -r requirements.txt
python run.py                 # 0.0.0.0:8000
```

The Yahoo endpoint may be unavailable in restricted networks; the API and CSV-based functions remain separate from the Android offline engine.

### Frontend

```bash
cd frontend
npm ci
npm run dev                   # Vite proxies relative /api requests to FastAPI
```

### Tests and builds

```bash
cd backend && python -m pytest -q
cd ../frontend && npm ci && npm run build
cd ../android && gradle testDebugUnitTest assembleDebug assembleRelease
```

Debug APK output: `android/app/build/outputs/apk/debug/app-debug.apk`. Android CI also uploads debug and release APK artifacts from successful runs. Without owner-provided release signing secrets, release APK is unsigned; use debug APK for straightforward sideload testing.

## Data integrity notes

- Local Android import is user-supplied data and stored by instrument/timeframe. Parser treats timezone-less date-times as UTC; decimal-comma locale files need conversion before import.
- Provider data in the Python service is not an executable broker quote and its delay/availability is not guaranteed.
- No Render/Enzonic or other always-on hosted backend is required or selected. Do not add credentials to the APK or public repository.
- The overlay does not read/control MT5, capture screen content, or place orders.

## Documentation

- `DEVELOPMENT_ROADMAP.md` — implementation and verification status
- `documentation/ARCHITECTURE.md` — current component topology and limitations
- `documentation/TESTING.md` — test/build record
- `android/README.md` — Android build, APK and signing instructions
