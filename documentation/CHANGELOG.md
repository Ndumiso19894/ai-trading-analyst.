# Changelog

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
- Added Kotlin/Compose Android client source. APK was not built because JDK/SDK/Gradle absent.
- Tests: backend suite and frontend production build executed; live provider request blocked
  by sandbox networking.

## [0.1.0] — Phase 1 foundation
- Initial FastAPI API, React dashboard, project layout, docs and environment configuration.
