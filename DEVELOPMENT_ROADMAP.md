# Development roadmap and status

Updated 2026-11-17. Status distinguishes implemented code from externally deployed or
field-verified behavior.

## Web/backend groundwork — substantially implemented
- [x] FastAPI app, credential-free CORS, React/TypeScript dashboard, Vite API proxy
- [x] Yahoo chart adapter for XAUUSD=X; source/timestamp caveats, bounded memory cache and CSV fallback
- [x] CSV OHLC validation, duplicate handling, ordering, timeframe support and upload limits
- [x] Candlestick chart, indicators, heuristic swing structure and pivot support/resistance
- [x] Deterministic BUY/SELL/WAIT engine, MTF conflict gate, ATR levels, risk calculator
- [x] CSV backtest with next-bar-open entries, cost assumptions, stop-first collision handling,
  chronological split and performance metrics
- [x] SQLite signal history and best-effort outcomes
- [x] Screenshot upload validation and tested Pillow sampling (not full chart interpretation)
- [x] Enzonic FastAPI detection shim at `backend/main.py`, exposing original `app.main:app`

## Hosting — Enzonic Deploy build fix pushed, deployment not verified
- [x] Owner-supplied Enzica build diagnosis: build failed at FastAPI detection because app module
  was nested below the selected backend root.
- [x] Added a top-level `backend/main.py` re-export and a test checking it is the same FastAPI app.
- [ ] Wait for Enzonic to build this branch and reach Ready. Supplied production URL currently
  returns `DEPLOYMENT_NOT_FOUND`; no live API URL has been confirmed.
- [ ] Verify health and API routes; Yahoo market data depends on egress from Enzonic.
- [ ] Build new Android APK with verified URL and test device/backend integration.
- Enzonic Deploy Free has bounded invocation/bandwidth/compute allowances; free server mode sleeps
  after 15 idle minutes. No paid plan is selected. See `documentation/DEPLOYMENT.md`.
- Render is no longer selected; its Blueprint was removed.

## Partial / limited functionality
- Screenshot upload always returns WAIT after color sampling; no OCR/vision model or reliable
  image-derived structure/prices/patterns.
- Yahoo endpoint is unofficial, may be delayed/rate-limited and has no SLA. Sandbox egress blocks
  direct Yahoo requests. Deployed source availability is unverified.
- Price action covers basic pivots only; named patterns, full BOS/CHoCH, supply/demand and
  false-breakout logic remain outstanding.
- Signal outcome refresh is best-effort, TP1/SL only; no broker fill simulation or full costs.
- No API authentication, push notifications, durable hosted history, or browser E2E suite.

## Android — CI build verified, Enzonic URL pending
- [x] Kotlin/Compose API client, health check, configurable/persisted advanced endpoint,
  analysis/market/history/screenshot actions
- [x] Release networking is HTTPS-only; debug build permits HTTP for local testing
- [x] Android CI build workflow requires an HTTPS URL and smoke-tests before building
- [x] App default remains empty until a verified host is available; advanced URL setting remains
- [ ] Build a fresh CI APK after Enzonic smoke tests pass.
- [ ] Physical/emulator interaction against deployed HTTPS API and owner signing for release.

## Outstanding
- Enzonic deployment readiness, public API tests, Android APK built against live URL
- Confirm durable storage for SQLite history; do not assume function filesystem persistence
- Local vision model/OCR, screenshot interpretation, robust market-structure strategies
- Automated Android unit tests, device verification, API authentication/rate-limit/security review
- Independent XAU/USD source validation and longer-term historical strategy validation
