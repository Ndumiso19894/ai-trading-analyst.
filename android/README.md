# Android client

Native Kotlin + Jetpack Compose source consuming the FastAPI service. The app checks `/health`
on first launch when a default URL is configured. Render is no longer selected. Enzonic's
project URL is not live yet, so the default is blank until a real HTTPS URL passes checks.
Advanced connection settings retains the saved URL override. Release builds require HTTPS;
debug builds allow HTTP for local testing.

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

The last APK build ([run #44087781450](https://github.com/Ndumiso19894/ai-trading-analyst./actions/runs/44087781450))
embeds Render's URL and is stale for Enzonic. Do not use it as the Enzonic-connected app.
After the Enzonic deployment is live, manually run the workflow and provide its exact HTTPS URL;
the workflow smoke-tests endpoints before producing `goldlens-android-apks`. That future run
will be the download link for the Enzonic-ready APK. Without owner signing secrets, the release
APK is unsigned; the debug APK is installable for testing but uses an ephemeral CI key.
