# Development roadmap and status

Updated 2026-11-17. Status reflects code implemented and tests actually run; **implemented
is not equivalent to validated on a real broker/feed**.

## Web/backend groundwork — substantially implemented
- [x] FastAPI app, environment configuration, CORS, React/TS dashboard, API proxy
- [x] Free Yahoo chart source integration for XAUUSD=X; source/timestamp and delayed-data
  caveats; bounded 60-second process cache; CSV offline fallback
- [x] CSV OHLC validation, duplicate removal, ordering, timeframe support, file limits
- [x] Candlestick chart via Lightweight Charts
- [x] EMA 20/50/100/200, RSI, MACD, ATR, Bollinger, stochastic calculations
- [x] Swing pivot structure + support/resistance clusters
- [x] Deterministic BUY/SELL/WAIT engine and explanation; ATR-based 1R/2R targets
- [x] Multi-timeframe comparison; conflicts/no-confirmation do not present a bullish/bearish
  consensus
- [x] Risk calculator with configurable contract multiplier and <=5% risk input cap
- [x] CSV backtest; next-bar-open, ATR exits, costs, conservative stop-first bar collision,
  chronological 70/30 split and metrics
- [x] SQLite signal history, outcome refresh endpoint, outcome counts
- [x] Responsive web pages for all requested sections, with features represented honestly
- [x] Screenshot upload input and tested local image validation/color sampling **only**

## Partial / limited
- Screenshot interpretation is NOT full chart-reading: no OCR, vision model, structure
  extraction or trade call. It responds WAIT with uncertainty and crude color counts.
- Yahoo source is unofficial, possibly delayed, rate-limited, may change and has no SLA;
  sandbox egress currently blocks its host. Live-source operation was not verified here.
- Provider cache is in-process memory only (lost on restart); signals use local SQLite.
- Price action only covers basic swing highs/lows; named candle patterns, robust BOS/CHoCH,
  supply/demand zones and false-breakout confirmation remain future work.
- Outcome refresh is best-effort based on provider 1h OHLC, TP1/SL only, no broker fills,
  fees or quote-quality controls. Expiry is a fixed 120-bar heuristic; TP2 is not included in outcome accounting.
- No production deployment hardening, authentication, cloud synchronization or push alerts.
- Frontend build was verified; browser-driven end-to-end test suite is not present.

## Android — build verified; app feature scope remains partial
- [x] Kotlin / Jetpack Compose source, analysis/market/history API actions and screenshot picker
- [x] Secure environment-variable release signing configuration; no credentials committed
- [x] GitHub Actions build passed on 2026-11-17 with JDK 17, Android API 35, Build Tools 35.0.0 and Gradle 8.9; `testDebugUnitTest`, `assembleDebug`, and `assembleRelease` tasks succeeded.
- [x] CI uploaded signed-debug and unsigned-release APKs as a 30-day Actions artifact.
- [ ] No Android unit test source or physical/emulator device validation yet; release APK remains unsigned until owner signing secrets are configured.
- [ ] Local toolchain download remains blocked in sandbox; artifact download from this sandbox is also blocked by its artifact-storage egress restriction.

## Not started / outstanding
- True local vision model integration, OCR, chart-pattern/screenshot inference tests
- Robust supply/demand, candle patterns, false breakout/BOS/CHoCH classifier
- Persistent candle cache, complete outcome lifecycle and costs/contract-aware performance
- Automated frontend tests, full browser E2E; Android compile/device tests and APK
- Notifications, authentication/security review, data licenses review
- Gold-market analysis validation across independent feeds and known historical cases
