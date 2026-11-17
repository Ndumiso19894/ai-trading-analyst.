# Development roadmap and status

Updated 2026-11-17. Status distinguishes implemented code from externally deployed or
field-verified behavior.

## Web/backend groundwork — substantially implemented
- [x] FastAPI app, CORS, React/TypeScript dashboard, Vite API proxy
- [x] Yahoo chart adapter for XAUUSD=X; source/timestamp caveats, bounded memory cache and CSV fallback
- [x] CSV OHLC validation, duplicate handling, ordering, timeframe support and upload limits
- [x] Candlestick chart, indicators, heuristic swing structure and pivot support/resistance
- [x] Deterministic BUY/SELL/WAIT engine, MTF conflict gate, ATR levels, risk calculator
- [x] CSV backtest with next-bar-open entries, cost assumptions, stop-first collision handling,
  chronological split and performance metrics
- [x] SQLite signal history and best-effort outcomes
- [x] Screenshot upload validation and tested Pillow sampling (not full chart interpretation)
- [x] Render Free Blueprint (`render.yaml`) for the FastAPI monorepo and `/health` probe

## Hosting — prepared, not deployed
- [ ] Owner must authorize Render by signing in and connecting this GitHub repo/branch.
  This Arena session has no Render account credentials or connected Render deployment tool.
- [ ] Deploy Blueprint, confirm the actual hostname, and smoke-test the public endpoints.
- [ ] Rebuild/test Android against the confirmed live URL. APK currently targets the Blueprint's
  expected Render hostname, which is not live/verified yet.
- Render Free is $0 but sleeps, has ephemeral local files and no persistent disk; SQLite signal
  history will be lost on restart/sleep. Details in `documentation/DEPLOYMENT.md`.

## Partial / limited functionality
- Screenshot upload always returns WAIT after color sampling; no OCR/vision model or reliable
  image-derived structure/prices/patterns.
- Yahoo endpoint is unofficial, may be delayed/rate-limited and has no SLA. Sandbox egress blocks
  direct Yahoo requests. Deployed source availability is unverified.
- Price action covers basic pivots only; named patterns, full BOS/CHoCH, supply/demand and
  false-breakout logic remain outstanding.
- Signal outcome refresh is best-effort, TP1/SL only; no broker fill simulation or full costs.
- No API authentication, push notifications, durable hosted history, or browser E2E suite.

## Android — CI build verified, device test outstanding
- [x] Kotlin/Compose API client, health check, configurable/persisted advanced endpoint,
  analysis/market/history/screenshot actions
- [x] Release networking is HTTPS-only; debug build permits HTTP for local testing
- [x] CI built v0.3.0 APKs with JDK 17, Android API 35, Build Tools 35 and Gradle 8.9; current artifact run `44069079549`
- [x] Default HTTPS host embedded in app and advanced saved override
- [ ] Owner-signed release APK (requires owner-managed key secrets)
- [ ] Physical/emulator interaction and test against deployed HTTPS API
- Local SDK/JDK download and CI artifact download are blocked from the sandbox; CI artifact is
  available through its GitHub Actions run in a browser.

## Outstanding
- Manual Render authorization/deployment and post-deploy endpoint verification
- Durable signal history storage; Render Free disk is ephemeral, free Postgres expires after 30d
- Local vision model/OCR, screenshot interpretation, robust market-structure strategies
- Automated Android unit tests, device verification, API authentication/rate-limit/security review
- Independent XAU/USD source validation and longer-term historical strategy validation
