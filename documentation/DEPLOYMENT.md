# Optional backend and offline Android operation

GoldLens does not require a permanently hosted backend. The Android app's imported-candle analysis, local database, risk calculator and CSV workflow are designed to operate offline. Do not configure a production server URL unless the endpoint is real, owned/authorized and verified by its operator. No always-on host is selected for this project.

## Existing optional FastAPI service

For local development:

```bash
cd backend
pip install -r requirements.txt
python run.py
```

The service is intended for local development/reference and optional features. Do not place credentials in Android source, Gradle constants, public GitHub, or APKs. Production deployment, access control, durable storage, licensing review and provider availability require separate owner configuration and verification.

## External market data caveat

The backend currently integrates the unofficial Yahoo Finance chart endpoint for XAU/USD only. It is free/keyless to call but may be delayed, rate-limited or changed, has no SLA, and is not guaranteed real-time. Verify current terms, ticker availability, usage limits and licensing before use or redistribution. The Android app does not currently call this provider directly.

## Android APK build

The GitHub Actions workflow runs unit tests and packages debug and release APKs. Without owner-controlled release signing secrets, release output is unsigned. Use the debug artifact for sideload testing. CI success does not verify physical-device permissions, launcher appearance, external API access or Google Drive/GitHub synchronization.

## Cloud authorization

Google Drive and GitHub application-level backup/synchronization are not implemented. Arena's connected-app access is separate from OAuth consent for the Android app. Any future Android sync feature must use explicit user authorization, secure token storage, retryable uploads and local-first conflict handling; do not delete local data when remote operations fail.
