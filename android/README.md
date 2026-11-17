# GoldLens Android app

Native Kotlin + Jetpack Compose Android app. Local analysis, Room storage, CSV import/export, instrument selection, risk calculations and the user-started floating overlay do not require an API server. Optional FastAPI settings are advanced/reference functionality and are not the source of local Android market prices.

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

Release signing requires a keystore controlled by the app owner. Do not commit the keystore or passwords. For CI, repository owners may configure `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` as Actions secrets. Without those secrets, CI leaves the release APK unsigned; the debug APK is installable for testing.

## GitHub Actions

`.github/workflows/android-build.yml` runs Android unit tests and assembles debug/release APKs, then uploads the APKs as `goldlens-android-apks` for 30 days. CI verifies compilation, tests and packaging, not overlay behavior or phone compatibility. Install the downloaded `app-debug.apk` via Android's package installer; allow installation from the chosen download source if prompted.
