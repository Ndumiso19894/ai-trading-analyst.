# Changelog

## [0.3.0] — phone-first backend preparation (2026-11-17)
- Added a Render Free Blueprint for a stable HTTPS FastAPI service URL, health check, Virginia
  region and CORS configuration. It is not deployed: owner Render/GitHub authorization is needed.
- Android now checks health on launch, defaults to the Blueprint host, and stores an advanced
  endpoint override. Release networking requires HTTPS; HTTP remains debug-only.
- Added Render free-tier/sleep/ephemeral-history caveats and phone deployment steps.
- Android CI build with the new URL is pending; no public API deployment has been verified.

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
- Added Kotlin/Compose Android client source; initial local build blocked by missing toolchain.
- Added Android CI build workflow using JDK 17, Android API 35 and Gradle 8.9; successful run
  `44065622695` uploaded the signed-debug and unsigned-release APKs. No release secrets are
  stored in CI. Owner-supplied secrets enable release signing.
- Local Android tool installation and artifact retrieval are blocked by sandbox network
  restrictions; CI compilation and APK packaging were verified remotely.

## [0.1.0] — Phase 1 foundation
- Initial FastAPI API, React dashboard, project layout, docs and environment configuration.
