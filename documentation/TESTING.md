# Verification and testing

## Executed in this workspace

- Latest backend suite after CORS and endpoint changes: `python3 -m pytest -q` — 21 passed,
  one Starlette deprecation warning. Includes mocked market provenance and higher-timeframe
  analysis, CSV, history, risk, screenshot abstention, CORS and backtest endpoints.
- Render deployment: **not deployed or tested**; Render/GitHub account authorization is still
  required. Do not treat the Blueprint's predicted hostname as live.

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
- Android remote build: GitHub Actions run `44065622695` passed on commit `aca26e6`; JDK 17,
  Android API 35 / Build Tools 35.0.0, Gradle 8.9, `testDebugUnitTest`, `assembleDebug`,
  `assembleRelease`, and artifact upload all succeeded. APK artifact is `goldlens-android-apks`
  (15,125,543 bytes, 30-day retention). The Gradle unit-test task has no authored Android
  unit test cases yet. Release APK is unsigned because owner signing secrets were not set.
  Sandbox download of the artifact archive was blocked by its `productionresultssa...blob`
  storage host; download through the linked GitHub Actions run in a browser.

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
