# Verification and testing

## Executed in this workspace

- Backend suite previously passed: `python3 -m pytest -q` — 21 passed, one Starlette
  deprecation warning. Includes mocked provider behavior, CSV, history, risk, screenshot abstention,
  CORS and backtest endpoints. These are local tests, not deployed API checks.
- Enzonic account/server deployment: **not performed**. No server, real public host, or live API
  test exists. The owner must provision the advertised free Python server after checking current
  availability and panel resource limits. No payment/card details were requested or supplied.
- Render: abandoned. `render.yaml` has been removed; no Render deployment actions are part of the
  current work.
- Provider smoke test: Yahoo chart endpoint access from this sandbox failed due outbound TLS
  restrictions. No live market data was retrieved here.
- Android local toolchain: `java`, `gradle`, `kotlinc`, `ANDROID_HOME`, and
  `ANDROID_SDK_ROOT` are absent in this sandbox.
- Previous Android CI run `44070173197` succeeded and packaged APKs, but its BuildConfig embeds
  the now-abandoned Render URL. That artifact is stale and should not be installed as the
  Enzonic-connected app. Android-to-live-backend and physical-device tests are outstanding.
- The current app source does not assign an invented backend URL; empty default is intentional
  until Enzonic provisions a real address. GitHub Actions is manual-only and requires a backend
  URL, runs `scripts/smoke_live_api.py` first, then builds/embeds it. This workflow change still
  needs a successful CI run and an actual endpoint before its artifact can be called current/usable.

## Live smoke checks after owner provisioning

Manual Android workflow dispatch checks health, provider metadata, market candles, gold analysis,
signal history, risk calculation, screenshot upload and CORS. Market and analysis checks need
working Yahoo egress from the host. Screenshot endpoint's expected behavior is validated image
sampling and `WAIT`, not AI chart interpretation. Review the deployment guide for the exact
URL and free plan caveats before dispatch.

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

## Android (requires separate toolchain)

Install JDK 17, Android SDK 35 and Gradle 8.9+, set `ANDROID_HOME`, then from `android/`
run `gradle testDebugUnitTest assembleDebug assembleRelease`. Build/device tests remain
outstanding until run against the real deployed HTTPS URL.
