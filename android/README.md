# Android client

Native Kotlin + Jetpack Compose source consuming the FastAPI service. The app checks `/health` on first launch only when a default endpoint has been configured.
Render is abandoned; no Enzonic server has been provisioned and no real Enzonic host is known.
The current default is intentionally blank rather than a made-up URL. The owner can set the
actual HTTPS address under Advanced connection settings, saved on-device. After the host is
verified, the same address will be embedded as the default in a fresh APK. Release builds
require HTTPS; debug builds allow HTTP for local testing.

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

Earlier Android artifacts (including [build #44070173197](https://github.com/Ndumiso19894/ai-trading-analyst./actions/runs/44070173197))
embed the abandoned Render URL and are stale; do not install them as the current phone app.
After Enzonic supplies a public HTTPS endpoint, manually run the workflow and provide that URL.
The workflow smoke-tests the deployed API first, then embeds the URL in a new APK and uploads
`goldlens-android-apks`; retrieve the fresh artifact from that run. Until this is done, no
Enzonic-connected APK exists. The CI debug signing key is ephemeral, so uninstall an older
CI-built debug APK before installing a newer one (or use an owner-signed release build to
preserve upgrade signing). Without the four owner signing secrets above, the release APK is
unsigned. On the phone, allow installation from the browser/file manager if prompted. Keep
the release key secure and backed up; losing it prevents signing compatible future updates.
