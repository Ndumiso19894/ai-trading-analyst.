# Android client

Native Kotlin + Jetpack Compose source consuming the FastAPI service. Current UI supports
backend URL configuration, analysis request, candles, signal history, screenshot selection
and upload. Emulator backend default: `http://10.0.2.2:8000`; for a physical phone, enter
an address reachable on your Wi-Fi. Cleartext HTTP is development-only.

## Local build

Requires JDK 17, Android SDK platform 35, Build Tools 35.0.0 and Gradle 8.9.

```bash
cd android
gradle testDebugUnitTest assembleDebug assembleRelease
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (installable with the Android
debug signature). Without a signing key, the release variant is emitted as
`app/build/outputs/apk/release/app-release-unsigned.apk`.

## Secure release signing

A release key must be created and safeguarded by the app owner. Never commit the keystore,
passwords, or signing secrets. With a JDK, generate a key interactively (keytool prompts for
passwords) and keep the keystore outside Git:

```bash
keytool -genkeypair -v -keystore "$HOME/goldlens-release.jks" -alias goldlens \
  -keyalg RSA -keysize 2048 -validity 10000
```

Set these environment variables in the build shell or secret manager; values are read by
the Gradle signing configuration and are not written into source:

```text
ANDROID_KEYSTORE_FILE=/secure/path/goldlens-release.jks
ANDROID_KEYSTORE_PASSWORD=<secret>
ANDROID_KEY_ALIAS=goldlens
ANDROID_KEY_PASSWORD=<secret>
```

Then run `gradle assembleRelease`. The GitHub Actions build deliberately does **not** sign
a release APK because no owner-controlled key/secret is available. It uploads a signed
 debug APK (for device testing) and an unsigned release APK. Actions artifacts are retained
for 30 days; open the repository's Actions → Android build run → Artifacts to download.

The CI workflow builds with JDK 17, API 35 and Gradle 8.9 on a standard GitHub runner.
Compilation status is only known after the workflow run completes. On the current branch,
use the run linked to the Android build commit.
