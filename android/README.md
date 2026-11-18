# GoldLens Android app

Native Kotlin + Jetpack Compose Android app. Local analysis, Room storage, CSV import/export, instrument selection, risk calculations, historical backtesting and the user-started floating overlay do not require an API server. Optional FastAPI settings are advanced/reference functionality and are not the source of local Android market prices.

## Android market data and multi-timeframe analysis

Overview has a user-started Yahoo Finance chart request, implemented behind a replaceable `MarketDataProvider`. It is an unofficial historical-chart endpoint, not an exchange or broker feed. Delay status, symbol coverage (especially metals), service terms, and licensing have not been independently verified; the app labels provider candles as historical and displays candle/reference timestamps, fetch time, provider identity and a caveat. Missing bid/ask is shown as unavailable, never inferred. Imported CSV and fetched charts replace candles only for the selected instrument/timeframe and retain their source label in Room.

Multi-timeframe views use UTC-aligned aggregation only when every source candle in a target bucket exists. No finer bars are synthesized. A genuinely opposing available higher-timeframe direction gates the setup to WAIT. Missing or neutral higher frames are called out in the signal explanation but do not erase a valid primary-timeframe BUY/SELL signal; provider-source analysis still waits on stale/future candles. Signals are calculated automatically after CSV import or provider fetch and can be recalculated manually. BUY/SELL cards show the reason, supporting evidence, cautions, candle age, and theoretical entry/stop/target method; WAIT explains why no setup was issued. The full trend assessment needs at least 200 valid candles. Evidence score is not a probability, and levels are not broker-specific trade instructions. Historical CSV data can still be analyzed without a current-data freshness claim.

## Background monitoring and Telegram alerts

Settings → Background market monitoring starts a WorkManager scan: an immediate run plus a periodic run every 15 minutes (Android's minimum periodic interval) across all supported instruments on the currently selected timeframe. Each scan fetches the Yahoo chart snapshot, analyzes it locally, and—when a new closed candle appears—replaces the stored candles and saves the analysis in Room. Android may defer or batch background work (Doze, battery optimization, OEM task killers), so scans are best-effort and never guaranteed at exact 15-minute marks. Yahoo data remains unofficial historical/reference data: it can be delayed, rate-limited, or unavailable for some symbols, and it is not an executable broker quote.

Only new directional states are alerted: when an instrument/timeframe moves from WAIT (or the opposite direction) into BUY or SELL, GoldLens posts a local notification and, if enabled, sends a Telegram message. Repeated scans with the same direction are suppressed, and WAIT results are never sent. Telegram messages include the Yahoo source caveat, formatted entry/stop/target levels, and the closed-candle timestamp; they never place trades.

To enable Telegram delivery:

1. In Telegram, open `@BotFather`, send `/newbot`, and copy the bot token.
2. Open your new bot, press Start, and send it any message.
3. In GoldLens Settings, paste the token (it is encrypted with an Android Keystore AES-GCM key on this device and never logged), then tap "Find chat ID from my bot messages" or enter the chat ID manually.
4. Turn on "Send new BUY/SELL signals to Telegram", tap "Save Telegram settings", then "Send test message" to verify delivery.

Never paste the bot token into chat or commit it to source control. If the token is lost or revoked in BotFather, enter a new one in Settings; the old encrypted copy is replaced.

## Backtesting limits

The offline EMA(20/50) crossover baseline enters at the next bar open, uses prior-bar ATR for 1.5 ATR stops and 3 ATR targets, checks same-bar exits conservatively (stop first), and reports a chronological 70/30 split. Spread, slippage and commission are explicit user-entered price-unit assumptions. Metrics are per price unit—not account currency, a broker fill model, or a forecast. It does not model lot size, FX conversion, funding, margin, variable spread, market impact or corporate/broker execution rules. Backtest runs are stored locally.

## Build

Requires JDK 17, Android SDK platform 35, Build Tools 35.0.0 and Gradle 8.9.

```bash
cd android
gradle testDebugUnitTest assembleDebug assembleRelease
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Without an owner-controlled signing key, release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`.

## Icon

Launcher art is in `../assets/goldlens-app-icon.png`; density-specific legacy icons and the Android 8+ adaptive icon resources are under `app/src/main/res/`. Manifest declares both standard and round launcher icons.

## Broker specifications

The instrument list is a symbol/display template. It does not assert broker-specific symbol aliases, contract sizes, tick values, minimum lots, volume steps or market sessions. Check these in the selected broker's contract specification before using risk calculations. Android risk calculation requires an explicit contract size or tick size plus tick value; it does not perform account-currency conversion.

## Permissions and source limitations

The overlay starts only after user activation and Android overlay permission. It displays locally stored imported candles; it does not read or control MT5. The current Android image action can choose an existing image for the optional API; no MediaProjection screen capture, crop workflow or chart-recognition engine is implemented.

## Signing

Release signing requires a keystore controlled by the app owner. Do not commit the keystore or passwords. For CI, repository owners may configure `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` as Actions secrets. Without those secrets, CI leaves the release APK unsigned; the debug APK is installable for testing. A standard debug-signed APK cannot update an existing installation signed by a different certificate. The debug variant uses the owner release key only when that keystore is provided to the build; upgrade compatibility still must be verified against the certificate on the installed app.

## GitHub Actions

`.github/workflows/android-build.yml` runs Android unit tests and assembles debug/release APKs, then uploads the APKs as `goldlens-android-apks` for 30 days. CI verifies compilation, tests and packaging, not overlay behavior or phone compatibility. Install the downloaded `app-debug.apk` via Android's package installer; allow installation from the chosen download source if prompted.
