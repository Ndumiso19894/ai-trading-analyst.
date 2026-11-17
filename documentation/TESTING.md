# Verification and testing

## Executed in this workspace

- Latest backend suite after CORS and endpoint changes: `python3 -m pytest -q` — 21 passed,
  one Starlette deprecation warning. Includes mocked market provenance and higher-timeframe
  analysis, CSV, history, risk, screenshot abstention, CORS and backtest endpoints.
- Render deployment: owner reports repository connection. Public check of the planned
  `https://goldlens-xau-api-ndumiso19894.onrender.com/health` returned a plain `Not Found`
  response; this is not a successful health check and no public API deployment is confirmed.

- Backend: `cd backend && python3 -m pytest -q` — latest suite includes indicator bounds,
  OHLC validation/resampling, deterministic analysis, backtest metrics, CSV API upload,
  screenshot decode/WAIT behavior, risk math and configured ticker restriction.
- Frontend: `cd frontend && npm run build` — TypeScript typecheck + Vite production build.
- Provider smoke test: called Yahoo chart endpoint for XAUUSD=X; **failed** because sandbox
  outbound network closed TLS to provider. No live data was retrieved; endpoint use is not
  verified in this environment.
- Android local toolchain: `java`, `gradle`, `kotlinc`, `ANDROID_HOME`, and
  `ANDROID_SDK_ROOT` are absent. Debian package mirrors, GitHub release-asset redirects, and
  Google SDK downloads were blocked from this sandbox.
- Android remote build: GitHub Actions run `44087781450` passed on commit `feace20`; JDK 17,
  Android API 35 / Build Tools 35.0.0, Gradle 8.9, `testDebugUnitTest`, `assembleDebug`,
  `assembleRelease`, and artifact upload all succeeded. APK artifact `goldlens-android-apks`
  (15,138,497 bytes, 30-day retention) embeds the planned Render Blueprint host. The
  Gradle unit-test task has no authored Android test cases. Release APK is unsigned without
  owner signing secrets. Sandbox download of the ZIP was blocked by its
  `productionresultssa...blob` storage host; retrieve it through the Actions run in a browser.
- Android-to-live-backend test: **not possible yet** because the planned Render hostname has
  not passed `/health`. API route behavior is covered by local tests/mocked provider checks.

Always rerun commands against the current source before reporting a result.

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
run `gradle assembleDebug`. Build/device tests remain outstanding.
