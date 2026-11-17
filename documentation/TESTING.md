# Verification and testing

## Executed in this workspace

- Backend: `cd backend && python3 -m pytest -q` — latest suite includes indicator bounds,
  OHLC validation/resampling, deterministic analysis, backtest metrics, CSV API upload,
  screenshot decode/WAIT behavior, risk math and configured ticker restriction.
- Frontend: `cd frontend && npm run build` — TypeScript typecheck + Vite production build.
- Provider smoke test: called Yahoo chart endpoint for XAUUSD=X; **failed** because sandbox
  outbound network closed TLS to provider. No live data was retrieved; endpoint use is not
  verified in this environment.
- Android: build attempt not available. `java`, `gradle`, `kotlinc`, `ANDROID_HOME`, and
  `ANDROID_SDK_ROOT` are absent. No APK was generated.

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
