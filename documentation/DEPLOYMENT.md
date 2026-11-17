# Phone-first backend deployment (Render Free)

## Deployment status

A Render Blueprint is included in `render.yaml` for the current Arena branch. It configures a
Python FastAPI web service named `goldlens-xau-api-ndumiso19894`, Virginia region, free
instance, API health check and wildcard CORS without cookies. The owner reports connecting the
GitHub repository to Render. However, the planned URL
`https://goldlens-xau-api-ndumiso19894.onrender.com` returned a plain `Not Found` response on
`/health` when checked after the repo connection was reported. The deployment is therefore not
confirmed live; do not treat that host as working until it returns the app's expected JSON.

## Create it from a phone

Render requires an account and Git provider authorization. From a mobile browser:

1. Open [Render Dashboard](https://dashboard.render.com/) and sign in/create an account.
2. Authorize GitHub repository access when prompted.
3. Choose **New → Blueprint** and select `Ndumiso19894/ai-trading-analyst.`.
4. Select branch `arena/8a1d0765-ai-trading-analyst` (the branch containing `render.yaml`).
5. Review the blueprint. Keep the service plan at **Free**; do not select or upgrade to a paid
   instance. Deploy the blueprint.
6. Wait for the deployment to show **Live**. Copy the actual `https://…onrender.com` URL shown
   on the service page. Check `https://<actual-host>/health` in the phone browser. Expected JSON
   has `status: ok` and app version `0.3.0`.
7. Check `/api/providers`, `/api/market/candles?timeframe=1h&period=5d`,
   `/api/analysis?timeframe=1h&period=3mo`, `/api/signals`,
   `/api/risk/calculate` (POST JSON), and `/api/screenshot/analyze` (multipart PNG/JPEG).
   Market/analysis endpoints depend on Yahoo Finance being reachable from the host; they can
   return 502 if the provider blocks requests. Screenshot processing always abstains with WAIT.

## Free plan behavior and data caution

Render's official Free plan is $0, but is intended for testing/hobby use rather than
production. Free web services sleep after 15 minutes without requests; wake-up takes about a
minute. The allowance is 750 free instance-hours per workspace per calendar month, and
exhaustion suspends free services until the next month. The service may restart at any time.
Free services have ephemeral filesystems: SQLite signal history is lost on sleep/restart or
redeploy. Persistent disks are not available to free web services. Render's free Postgres
expires after 30 days, so this project does not use it as a purported permanent database.
Render also warns that unusually high service-initiated external API traffic may cause
suspension. Details: [Render Free limits](https://render.com/docs/free).

Render supports free web services and managed TLS; requests use HTTPS by default. The app's
release manifest disallows cleartext HTTP. Android's debug manifest allows cleartext only for
development; the app default uses HTTPS. API CORS is wildcard and credential-free; native
Android networking itself is not subject to browser CORS.

## Build and smoke-test an APK with the actual URL

The Android Gradle build defaults to the Blueprint's intended host, but the host is **not yet
verified**. After deployment, open GitHub Actions → Android build → **Run workflow** and pass
the actual API root in `backend_url` (even if it matches the default). A manual run first
smoke-tests health, provider metadata, market candles, gold analysis, signal history, risk,
screenshot upload and CORS, then embeds that HTTPS URL in the APK. Market candles/analysis
checks fail if the upstream Yahoo provider returns an error. You can instead enter a custom
HTTPS endpoint in the app's Advanced connection settings (it is saved on-device).

Build artifacts are available on the workflow run page for 30 days. The debug APK is signed
for testing; a production release APK requires owner-managed signing secrets as described in
`android/README.md`.
