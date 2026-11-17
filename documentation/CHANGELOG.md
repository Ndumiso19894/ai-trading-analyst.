# Changelog

## [0.3.1] — Enzonic Free Python Hosting preparation (2026-11-17)
- Abandoned Render as explicitly requested and removed `render.yaml`; no Render service was
  created. Replaced deployment guidance with the Enzonic Free Python server flow.
- Added `backend/start.sh` for Uvicorn, `0.0.0.0` binding and panel-provided `PORT` support.
- Removed the planned Render URL from Android. Until a real Enzonic HTTPS hostname exists, the
  BuildConfig default is blank and the UI clearly asks for a deployed URL in advanced settings.
- Android workflow is manual-only while no actual URL exists. It requires the verified HTTPS
  URL, smoke-tests the live API before building, then embeds the supplied endpoint.
- Checked Enzonic's official Python hosting page. It advertises free/no card, one server/account
  subject to capacity, 24/7, Python 3.10–3.12, `requirements.txt`, and custom Uvicorn commands.
  Live page's resource figures differ from its indexed metadata; panel resource availability
  must be confirmed by the owner.
- No Enzonic account/server was created or accessed, no actual URL is known, no public APIs have
  been tested, and no fresh APK with an Enzonic URL exists yet.

## [0.3.0] — phone-first backend preparation (2026-11-17)
- Android health check, saved advanced HTTPS endpoint override, analysis/market/history/screenshot
  actions; release networking HTTPS-only and HTTP debug-only.
- Backend CORS and API tests, backend deployment preparation, phone-first docs.
- Previous Android build run `44070173197` packaged APKs embedding the Render target. That target
  is now abandoned; this old artifact is stale and is not an Enzonic deployment/build result.

## [0.2.0] — analysis and application milestone (2026-11-17)
- Added Yahoo chart data adapter with source caveats and bounded memory cache; CSV import and
  OHLCV validation remain an offline market-data option.
- Added EMA/RSI/MACD/ATR/Bollinger/Stochastic calculations, pivot structure and S/R, deterministic
  BUY/SELL/WAIT analysis, 4H aggregation, multi-timeframe endpoint and SQLite signal history.
- Added risk calculator, chronological CSV backtest with cost assumptions and out-of-sample
  report, outcome-refresh endpoint and backtest/performance UI.
- Replaced static dashboard with responsive functional React client and candlestick chart.
- Added safe image upload, Pillow decode and color sampling that always abstains (not a chart
  vision engine); tested API behavior.
- Added Kotlin/Compose Android client and Android CI workflow; earlier APK builds were successful.

## [0.1.0] — Phase 1 foundation
- Initial FastAPI API, React dashboard, project layout, docs and environment configuration.
