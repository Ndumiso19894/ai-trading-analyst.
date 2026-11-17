# Verification and testing

## Executed in this workspace

- Backend suite after adding the Enzonic shim: `cd backend && python3 -m pytest -q` — 22 passed,
  one Starlette deprecation warning.
- New entrypoint import check: `cd backend && python3 -c 'import main; from app.main import app; assert main.app is app'` — passed.
- Enzonic deployment: owner supplied an Enzica diagnosis that the build failed detection because
  no FastAPI app was visible at the configured `backend/` root. Added `backend/main.py` to expose
  the original app as `main:app`. This source change has not yet been confirmed by a new Enzonic
  build. The supplied URL returns Enzonic `DEPLOYMENT_NOT_FOUND` on `/`, `/health`, `/api/providers`
  and `/docs`; there is no live API deployment verified yet.
- Render deployment: no longer selected.
- Provider test: Yahoo chart endpoint access from this sandbox failed due outbound TLS
  restrictions. No live market data was retrieved here.
- Android local toolchain is unavailable in this sandbox. The last CI build (run `44087781450`)
  embeds the stale Render URL; it is not an Enzonic-ready APK. A fresh build is gated on the
  Enzonic URL passing live API smoke checks.

## Live smoke checks after deployment

Manual Android workflow dispatch requires the exact public HTTPS URL and runs
`scripts/smoke_live_api.py` before building. It checks health, providers, market candles, gold
analysis, signal history, risk math, screenshot upload and CORS. Market/analysis checks require
Yahoo access from the host. Screenshot endpoint's expected behavior is local image sampling and
`WAIT`, not AI chart interpretation. A smoke-test failure prevents APK packaging.

## Backend commands

```bash
cd backend
pip install -r requirements.txt
python -m pytest -q
```

## Frontend commands

```bash
cd frontend
npm ci
npm run build
```

## Android

The GitHub Actions workflow runs `testDebugUnitTest assembleDebug assembleRelease`. Device
integration remains outstanding until the deployed Enzonic HTTPS URL passes smoke tests.
