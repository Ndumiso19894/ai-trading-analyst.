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
- [x] Enzonic Python-host startup script: Uvicorn binds 0.0.0.0 and honors `PORT`

## Hosting — Enzonic selected, not deployed
- [x] Reviewed Enzonic official Python page: Free Forever/no card is advertised; live page says
  512 MB RAM, 50% CPU, 10 GB storage and 24/7 uptime, one server/account subject to capacity.
- [!] The same page has conflicting indexed metadata (1 GB/100% CPU/5 GB); verify exact free
  allocation in the account panel before provisioning. No card or paid choice is authorized.
- [ ] Owner Enzonic sign-in/GitHub repository authorization and free Python server provisioning.
- [ ] Confirm panel provides external HTTPS routing to the Uvicorn port, then obtain actual host.
- [ ] Deploy from `backend/` with requirements and `start.sh`; verify health and all requested APIs.
- [ ] Trigger Android CI manually with actual URL; smoke tests must pass before URL is embedded.
- No service or Enzonic Deploy project has been created; there is no public API URL yet.

## Partial / limited functionality
- Screenshot upload always returns WAIT after color sampling; no OCR/vision model or reliable
  image-derived structure/prices/patterns.
- Yahoo endpoint is unofficial, may be delayed/rate-limited and has no SLA. Sandbox egress blocks
  direct Yahoo requests. Deployed source availability is unverified.
- Free Python server's advertised resources are constrained; NumPy/Pandas workload memory must
  be measured on the provisioned server. Do not upgrade without owner approval.
- Price action covers basic pivots only; named patterns, full BOS/CHoCH, supply/demand and
  false-breakout logic remain outstanding.
- Signal outcome refresh is best-effort, TP1/SL only; no broker fill simulation or full costs.
- No API authentication, push notifications, durable hosted history, or browser E2E suite.

## Android — prior CI build verified; Enzonic release pending
- [x] Kotlin/Compose API client, health check, configurable/persisted advanced endpoint,
  analysis/market/history/screenshot actions
- [x] Release networking is HTTPS-only; debug build permits HTTP for local testing
- [x] App no longer points at a Render or invented host; unset URL is shown as not configured.
- [ ] Build new CI APK with verified Enzonic URL after smoke-test pass.
- [ ] Physical/emulator interaction against deployed HTTPS API.
- [ ] Owner-signed release APK (requires owner-managed signing key secrets).
- Prior CI APKs embed abandoned Render URL; they are stale and not recommended for installation.

## Outstanding
- Enzonic account authorization, availability check, free server provisioning, actual HTTPS host,
  public API verification and backend-connected APK build.
- Confirm SQLite persistence/backups on Enzonic; local signal history may be lost on server reset.
- Local vision model/OCR, screenshot interpretation, robust market-structure strategies
- Automated Android unit tests, device verification, API authentication/rate-limit/security review
- Independent XAU/USD source validation and longer-term historical strategy validation
