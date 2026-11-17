# Testing and verification record

## Previous verified runs

- Backend pytest passed on 2026-11-17: `22 passed, 1 warning` (recorded before current icon/instrument/CSV-export work).
- Android CI run `44123829333` passed on commit `2b9d3cd909572a197c4a99dce10dfc6ed5a9a5fe`: Gradle `testDebugUnitTest`, `assembleDebug` and `assembleRelease`; APK artifacts were uploaded. This run predates the current instrument selector/export/icon changes.
- Android manifests and workflow YAML parsed; `git diff --check` passed on the previous checkpoint.

## Current changes requiring verification

Current uncommitted work adds adaptive/legacy launcher resources, an instrument registry and selection persistence, symbol-aware Room candle imports, UTC CSV export, and broker specification validation changes. Do not treat these as tested until a fresh Android CI run passes on the resulting commit.

## Verification limits

- No emulator/physical-device testing has been performed. Overlay permission and foreground-service behavior need real Android validation.
- No Android MediaProjection or screenshot capture behavior exists to test.
- No frontend changes or frontend build have been made during this iteration. The last known backend tests do not validate the Android source.
- Optional Yahoo data may be unavailable in restricted networks; no live/delayed quote success is claimed for Android.
