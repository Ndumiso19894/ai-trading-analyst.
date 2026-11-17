# Android client

Native Kotlin + Jetpack Compose source consuming the same FastAPI service. Current UI
supports backend URL configuration, automatic analysis request, market candles, signal
history, screenshot picker/upload and uncertainty-safe display of API response.

## Build

Requires JDK 17, Android SDK platform 35 and a Gradle 8.9-compatible installation. The
current workspace has no Java, Gradle or Android SDK, so compilation and APK generation
have **not** been performed.

```bash
cd android
gradle assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

The default emulator API address is `http://10.0.2.2:8000`. Change it in the app for a
physical phone. Cleartext traffic is enabled for local development only; do not deploy
that setting to a public production build. Do not expose the API publicly without HTTPS,
authentication and network controls.
