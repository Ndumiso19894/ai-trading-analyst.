# Testing and verification record

## Previous verified runs

- Backend pytest passed on 2026-11-17: `22 passed, 1 warning` (recorded before current icon/instrument/CSV-export work).
- Android CI run `44123829333` passed on commit `2b9d3cd909572a197c4a99dce10dfc6ed5a9a5fe`: Gradle `testDebugUnitTest`, `assembleDebug` and `assembleRelease`; APK artifacts were uploaded. This run predates the current instrument selector/export/icon changes.
- Android manifests and workflow YAML parsed; `git diff --check` passed on the previous checkpoint.

## Current checkpoint verification

- Android CI run `44134843661` passed on commit `002243dc760d81992f4124ef5b3a0be5b0759451`: `testDebugUnitTest`, `assembleDebug`, and `assembleRelease` succeeded; `goldlens-android-apks` and test-report artifacts were uploaded.
- This run verifies the adaptive/legacy icon resources, instrument registry/selection persistence, symbol-aware Room candle import, UTC CSV export and broker-spec validation compile and pass the current Kotlin unit tests.
- Python backend tests rerun in this iteration: `22 passed, 1 warning`.
- Frontend production build rerun in this iteration: `tsc -b && vite build` succeeded (Vite 6.4.6).
- Android manifest/resources XML, workflow YAML, generated PNG decoding and `git diff --check` passed.

## Verification limits

- No emulator/physical-device testing has been performed. Overlay permission and foreground-service behavior need real Android validation.
- No Android MediaProjection or screenshot capture behavior exists to test.
- No frontend changes or frontend build have been made during this iteration. The last known backend tests do not validate the Android source.
- Optional Yahoo data may be unavailable in restricted networks; no live/delayed quote success is claimed for Android.
