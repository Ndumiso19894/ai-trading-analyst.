# GoldLens development status

Updated 2026-11-17. This file separates shipped code from verified runtime behavior. It supersedes earlier hosting notes. Render and Enzonic are not used; normal Android calculations must not depend on a hosted service.

## Existing project preserved

The repository still contains the FastAPI backend, React/TypeScript dashboard, Kotlin/Jetpack Compose Android app, analysis/backtest code, SQLite signal history and GitHub Actions workflows. Android is the primary offline-capable product; the backend remains optional/reference functionality.

## Android capabilities implemented in source

- Kotlin indicators and an explainable BUY/SELL/WAIT engine; confirmed-pivot support/resistance on the supplied timeframe.
- Room storage for candle sets, analyses/zones, journal entries, settings, backtest records and alert-rule records. Current schema is v1; no migration history exists yet.
- CSV OHLC import through Android's document picker. Parser accepts common timestamp/date formats and case-insensitive OHLC column names, validates rows, orders UTC timestamps and de-duplicates timestamps. Import is applied immediately; there is no preview or configurable decimal-comma/timezone mode.
- CSV export of the selected local candle set through Android's document picker.
- Instrument registry and selector for 11 requested FX pairs plus XAU/USD, XAG/USD and XPT/USD. Display precision is a suggestion. Broker aliases are templates; tick values, contracts, volume limits and sessions are deliberately unset until verified for the user's broker.
- Offline risk calculation supports a user-entered contract multiplier or tick-size/tick-value pair. Account-currency conversion is not implemented.
- User-started floating overlay service with overlay permission flow, foreground notification, drag/expand/collapse/stop, local saved-candle analysis and instrument/timeframe labels.
- Generated launcher icon in Android adaptive and legacy density resources.

## Android capabilities not implemented or not verified

- No independent market-data provider is integrated into the Android app. Do not treat the optional backend URL as a required Android runtime dependency.
- The existing backend's only external provider is Yahoo Finance's unofficial chart endpoint, fixed by the API to XAU/USD. It is keyless/free to call but may be delayed, rate-limited or changed; it is not guaranteed real-time, and provider terms/availability must be checked before redistribution. No reliable provider coverage has been established for the full instrument registry.
- No MediaProjection consent/capture workflow, preview/crop UI or screenshot-derived chart recognition exists in Android. The Android screen only accepts a user-selected existing image for the optional backend endpoint; the backend color sampler abstains and does not identify candles, exact prices, instrument or timeframe.
- No Android backtesting engine/UI is connected. Existing historical CSV backtesting is in the Python backend and is not presented as native offline Android backtesting.
- No WorkManager alert scheduler, notification rules UI or dependable background monitoring. Room has an alert-rule table only.
- No Google Drive OAuth/backup, GitHub data snapshots or unified sync manager. Local Room data is not deleted by any sync process because no sync process exists.
- No three-mode Independent/MT5/Compare source selector, no validated structured MT5 bridge, no bid/ask/spread feed, and no true multi-timeframe Android analysis from separate datasets. Current Android analysis uses one user-imported instrument/timeframe candle set.
- No user CSV import preview or export of signals/journal/backtests/zones yet.
- The overlay, permission flow, Room persistence, and icon rendering need emulator/physical-device acceptance checks. CI verifies compile/unit tests/APK packaging only.

## Web/backend

- FastAPI and React/TypeScript remain in the repository. Backend source includes provider adapter, CSV endpoints, indicators/signals, heuristic support/resistance, risk endpoint, historical backtesting, screenshot color-sampling abstention and SQLite signal history.
- Python backtesting runs on uploaded OHLC data, uses next-bar-open entries, spread/slippage assumptions, conservative stop-first collisions and chronological out-of-sample evaluation. Its results are per-unit historical simulation, not account returns or a prediction.
- The React dashboard is a distinct optional client; it is not the Android app and its Yahoo provider path may be unavailable in a restricted network.

## Verification record

- Android GitHub Actions run `44123829333` passed on commit `2b9d3cd909572a197c4a99dce10dfc6ed5a9a5fe`: `testDebugUnitTest`, `assembleDebug` and `assembleRelease`, and uploaded debug/release APK artifacts. That build predates the current instrument/export/icon edits; those changes must receive a fresh CI run before claiming current-head build success.
- Backend pytest previously passed: 22 tests, 1 warning (recorded 2026-11-17). Rerun after backend changes.
- No physical-device or emulator verification has been performed.

## Next development steps

1. Build/test current Android changes and validate launcher icon on emulator/device.
2. Add automated preview/crop and CSV export tests plus safe Room migrations.
3. Implement a truthful provider abstraction and provider coverage after verifying licenses, update delay, symbol/timeframe availability, key requirements and rate limits. Keep provider secrets out of source/APKs.
4. Add Android multi-timeframe aggregation only when source data supports it; expose gaps and timeframe provenance.
5. Add MediaProjection consent/capture/preview/crop with explicit user action and privacy retention controls; retain honest recognition abstention unless validated OCR/vision is available.
6. Port backtesting to Kotlin and test causality/cost assumptions.
7. Add notification rules and WorkManager schedules with Android's periodic-work limits explained.
8. Add optional Drive/GitHub backups only after explicit user OAuth/authorization setup; keep Room authoritative and local records intact on errors.
9. Run backend tests, frontend build and Android CI after related changes; complete real-device checks before calling those paths operational.
