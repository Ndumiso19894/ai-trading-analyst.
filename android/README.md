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
debug signature). Without an owner signing key, the release variant is emitted as
`app/build/outputs/apk/release/app-release-unsigned.apk`.

## Secure release signing

A release key must be created and safeguarded by the app owner. Never commit a keystore,
passwords, or signing secrets. With JDK 17, generate a key interactively (keytool prompts
for passwords) and keep the keystore outside Git:

```bash
keytool -genkeypair -v -keystore "$HOME/goldlens-release.jks" -alias goldlens \
  -keyalg RSA -keysize 2048 -validity 10000
```

For a local build, export the following environment variables in the build shell:

```text
ANDROID_KEYSTORE_FILE=/secure/path/goldlens-release.jks
ANDROID_KEYSTORE_PASSWORD=<secret>
ANDROID_KEY_ALIAS=goldlens
ANDROID_KEY_PASSWORD=<secret>
```

For CI, add these **repository Actions secrets** (Settings → Secrets and variables →
Actions):

- `ANDROID_KEYSTORE_BASE64` — base64 encoding of the JKS file (Linux: `base64 -w0
  "$HOME/goldlens-release.jks"`; macOS: `base64 < ... | tr -d '\n'`).
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The workflow writes the decoded key only to its ephemeral runner temp directory and does
not print or commit it. Without these owner-provided secrets, CI intentionally leaves the
release APK unsigned. No signing credential has been created or exposed in this repository.

## Automated build and APK download

`.github/workflows/android-build.yml` builds with JDK 17, Android API 35, Build Tools 35
and Gradle 8.9. It runs `testDebugUnitTest`, `assembleDebug` and `assembleRelease`, then
uploads APKs as the `goldlens-android-apks` artifact for 30 days.

The verified run is [Android build #44065622695](https://github.com/Ndumiso19894/ai-trading-analyst./actions/runs/44065622695)
on commit `aca26e63`. Open the run in GitHub Actions → **Artifacts** →
`goldlens-android-apks`; download and unzip it on a phone or computer. CI verified both
`app-debug.apk` (signed with the standard debug key, installable for testing) and
`app-release-unsigned.apk`. The release APK is not installable until signed; configure the
four owner secrets above and rerun the workflow for a signed release. The sandbox could not
download the Action artifact archive because its blob-storage host is blocked, but the
artifact is available from the linked GitHub run for its 30-day retention. On the phone,
allow installation from the browser/file manager if prompted. Keep the release key secure
and backed up; losing it prevents signing compatible future updates.
