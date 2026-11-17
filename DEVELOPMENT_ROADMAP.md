# GoldLens development status

Updated 2026-11-17. This file separates shipped code from verified runtime behavior. It supersedes earlier hosting notes. Render and Enzonic are not used; normal Android calculations must not depend on a hosted service.

## Existing project preserved

The repository still contains the FastAPI backend, React/TypeScript dashboard, Kotlin/Jetpack Compose Android app, analysis/backtest code, SQLite signal history and GitHub Actions workflows. Android is the primary offline-capable product; the backend remains optional/reference functionality.

## Android capabilities implemented in source

- Kotlin indicators and an explainable BUY/SELL/WAIT engine; confirmed-pivot support/resistance on the supplied timeframe.
- Room storage for candle sets with source provenance, analyses/zones, journal entries, settings, backtest records and alert-rule records. Schema v2 includes a v1-to-v2 candle-provenance migration; migration is compile-tested in CI, but has not been exercised on a device database.
- CSV OHLC import through Android's document picker. Parser accepts common timestamp/date formats and case-insensitive OHLC column names, validates rows, orders UTC timestamps and de-duplicates timestamps. Import is applied immediately; there is no preview or configurable decimal-comma/timezone mode.
- CSV export of the selected local candle set through Android's document picker.
- Instrument registry and selector for 11 requested FX pairs plus XAU/USD, XAG/USD and XPT/USD. Display precision is a suggestion. Broker aliases are templates; tick values, contracts, volume limits and sessions are deliberately unset until verified for the user's broker.
- Offline risk calculation supports long and short positions with a user-entered contract multiplier or tick-size/tick-value pair. Account-currency conversion is not implemented.
- User-started floating overlay service with overlay permission flow, foreground notification, drag/expand/collapse/stop, local saved-candle analysis, source provenance and instrument/timeframe labels.
- Replaceable Android `MarketDataProvider` with a user-started Yahoo Finance unofficial chart request. Fetched bars are labeled historical, timestamped and stored locally; absent bid/ask is not synthesized. Delay, terms, limits and coverage (especially metals) remain unverified.
- UTC-complete candle aggregation, multi-timeframe assessment across 5m/15m/30m/1h/4h/1d, and conservative WAIT gating for missing/neutral/opposing higher timeframe context. Aggregation from incomplete source buckets abstains.
- Native offline EMA 20/50 crossover backtest with ATR exits, explicit user price-unit spread/slippage/commission assumptions, same-bar stop-first handling, chronological 70/30 reporting, local Room history and equity-curve visualization. It is a baseline historical simulation, not a broker fill or account-currency model.
- Generated launcher icon in Android adaptive and legacy density resources.

## Android capabilities not implemented or not verified

- Android and backend Yahoo Finance chart access is unofficial and not an exchange/broker feed. It may be delayed, rate-limited or changed; live status, terms, symbol coverage and suitability for redistribution have not been independently verified. Android labels responses as historical and blocks stale/future provider-source signals; no reliable coverage has been established for the full instrument registry. The backend's current provider path remains fixed to XAU/USD.
- No MediaProjection consent/capture workflow, preview/crop UI or screenshot-derived chart recognition exists in Android. The Android screen only accepts a user-selected existing image for the optional backend endpoint; the backend color sampler abstains and does not identify candles, exact prices, instrument or timeframe.
- Native backtesting is a single EMA/ATR baseline only; strategy customization, independent walk-forward folds, robust broker execution modeling, and live alert integration are not implemented.
- No WorkManager alert scheduler, notification rules UI or dependable background monitoring. Room has an alert-rule table only.
- No Google Drive OAuth/backup, GitHub data snapshots or unified sync manager. Local Room data is not deleted by any sync process because no sync process exists.
- No three-mode Independent/MT5/Compare selector, no validated structured MT5 bridge, and no genuine broker bid/ask/spread feed. Multi-timeframe Android analysis currently aggregates one selected candle series; it does not merge independent feeds or use separate provider endpoints per timeframe.
- No user CSV import preview or export of signals/journal/backtests/zones yet.
- The overlay, permission flow, Room persistence, and icon rendering need emulator/physical-device acceptance checks. CI verifies compile/unit tests/APK packaging only.

## Web/backend

- FastAPI and React/TypeScript remain in the repository. Backend source includes provider adapter, CSV endpoints, indicators/signals, heuristic support/resistance, risk endpoint, historical backtesting, screenshot color-sampling abstention and SQLite signal history.
- Python backtesting runs on uploaded OHLC data, uses next-bar-open entries, spread/slippage assumptions, conservative stop-first collisions and chronological out-of-sample evaluation. Its results are per-unit historical simulation, not account returns or a prediction.
- The React dashboard is a distinct optional client; it is not the Android app and its Yahoo provider path may be unavailable in a restricted network.

## Verification record

- Android GitHub Actions run `44142692767` passed on commit `1b286c64b61f47ace0d508e2f36ccb6deed8659e`: `testDebugUnitTest`, `assembleDebug` and `assembleRelease`, with the `goldlens-android-apks` artifact uploaded. The first run for this change set failed on a Kotlin `when` syntax error; the fix is in the passing commit.
- The artifact download was attempted from this sandbox but GitHub redirected to an external artifact-storage host that is not reachable here. Download the APKs from the [successful Actions run](https://github.com/Ndumiso19894/ai-trading-analyst./actions/runs/44142692767) (artifact retention is 30 days). APK signing certificate and upgrade compatibility were not verified; owner-secret access was blocked by GitHub integration permissions (HTTP 403).
- Backend pytest on 2026-11-17: 22 passed, 1 warning. Frontend `npm run build` on 2026-11-17: `tsc -b && vite build` succeeded.
- No physical-device or emulator verification has been performed. CI proves compilation, unit tests and packaging only.

## Remaining work

1. Obtain owner-confirmed provider terms, delay/availability and symbol/timeframe coverage; validate the adapter against a permitted live network environment and add provider-response tests.
2. Complete emulator/physical-device acceptance for fetch behavior, stale-data UI, Room v1 migration, CSV flows, overlay permissions and launcher icon.
3. Implement a real source-mode comparison only if a structured broker/MT5 feed becomes available; do not claim screenshot candle recognition or bypass MT5 Android API constraints.
4. Add CSV preview and export of signals, journal, backtest reports and zones.
5. Add notification rules and WorkManager schedules with Android background-execution limits explained.
6. Add optional Drive/GitHub backups only after explicit user OAuth/authorization setup; keep Room authoritative and local records intact on errors.
7. Extend backtesting beyond the baseline only with tests for causality, realistic costs, parameter selection and out-of-sample controls.
8. Keep all runtime paths independently testable; rerun Android CI, backend tests and frontend production build after related changes.
