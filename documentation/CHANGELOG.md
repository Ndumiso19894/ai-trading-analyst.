# Changelog

## [0.6.0-dev] — background monitoring and Telegram signal delivery (2026-11-17)
- Added WorkManager background scans (immediate + every 15 minutes when Android permits) across all supported instruments on the selected timeframe, refreshing stored Yahoo snapshots and posting local notifications for new BUY/SELL states only.
- Added optional Telegram delivery: user-configured bot token encrypted with Android Keystore AES-GCM, chat-ID discovery from bot messages, test message, and deduplicated BUY/SELL-only alerts with Yahoo source caveats.
- Added a signal-delivery policy unit test and bumped the Android version to 0.6.0 (versionCode 5).

## [0.5.0-dev] — offline instrument selector, CSV export and launcher icon (2026-11-17)
- Added selectable FX and precious-metal instrument registry with broker-specific tick/lot details left unset until verified.
- Candle persistence and analysis now retain the selected symbol; switching instrument/timeframe reloads only its saved local data rather than relabeling another dataset.
- Added normalized UTC OHLCV CSV export and round-trip/catalog/risk tests.
- Added the generated GoldLens launcher mark as adaptive and legacy Android icons.
- Updated documentation to distinguish Android offline features, optional backend capabilities, and remaining capture/cloud/backtest work. Android unit tests and debug/release packaging passed in CI run 44134843661 on commit 002243d.

## [0.3.3] — Enzonic Deploy FastAPI discovery fix (2026-11-17)
- Added `backend/main.py`, a top-level re-export of the existing `app.main:app`, because Enzica's
  build report said FastAPI detection only looked for an app entry at the selected backend root.
- Added a regression test asserting the root shim exports the original FastAPI instance.
- Enzonic is the selected hosting target after Render required payment; removed the Render
  Blueprint and cleared the Android default until Enzonic provides a live, verified HTTPS URL.
- The supplied `ai-trading-analyst.enzonic.dev` URL returns `DEPLOYMENT_NOT_FOUND`; no new
  Enzonic build or API integration is confirmed yet.

## [0.3.2] — Render resumed then stopped (2026-11-17)
- Render configuration and Android defaults were briefly restored at the owner's request.
- The planned Render health URL returned `Not Found`; the owner reported Render required payment,
  so it is no longer selected.

## [0.3.1] — Enzonic Free Python Hosting preparation (2026-11-17)
- Prepared a Uvicorn entrypoint and documented the separate Enzonic Python server.
- This effort was superseded when the owner attempted Enzonic Deploy; no server was provisioned.

## [0.3.0] — phone-first backend preparation (2026-11-17)
- Android health check, saved advanced HTTPS endpoint override, analysis/market/history/screenshot
  actions; release networking HTTPS-only and HTTP debug-only.
- Backend CORS and API tests, backend deployment preparation, phone-first docs.

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
- Added Kotlin/Compose Android client and Android CI workflow.

## [0.1.0] — Phase 1 foundation
- Initial FastAPI API, React dashboard, project layout, docs and environment configuration.
