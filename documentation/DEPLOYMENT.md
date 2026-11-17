# Enzonic Deploy (Free)

## Current status

The owner reports that Enzonic gave the project URL `https://ai-trading-analyst.enzonic.dev/`.
A public check currently returns Enzonic's `404 · DEPLOYMENT_NOT_FOUND` page at `/`, `/health`,
`/api/providers` and `/docs`; no app deployment is mapped to that address yet. This is an
Enzonic Deploy project URL, not a Render URL and not the separate Python Hosting server panel.
Render is no longer the selected provider, and `render.yaml` has been removed.

The build log supplied by the owner says dependencies installed, then FastAPI detection failed
because the app object was nested at `backend/app/main.py`, while the project root was `backend`.
The repository now has `backend/main.py`, a top-level shim that imports and exports the existing
`app` instance. It does not move or duplicate the application. A unit test asserts that the
shim and original module expose the same FastAPI object.

## Enzonic Deploy project settings

Use the Free plan only; Enzonic's official [Deploy page](https://enzonic.com/cloud/deploy)
advertises its free tier as $0/no-card. Do not choose a paid plan or upgrade. Official free
limits include 150 GB monthly bandwidth, 2 million function invocations, and 400 GB-hours of
function compute. It lists FastAPI support; functions have 1 GB memory and a 300-second timeout.
Free server mode is one 512 MB instance and sleeps after 15 idle minutes. Confirm the current
limits in the account console; do not add paid resources.

Set or confirm these project settings:

- Git repository: `Ndumiso19894/ai-trading-analyst.`
- Branch: the branch that Enzonic is meant to deploy. This Arena branch is
  `arena/8a1d0765-ai-trading-analyst`.
- Root directory: `backend`
- Runtime/framework: Python / FastAPI; Python 3.11 is supported.
- Dependencies: `requirements.txt` is in the selected root. Enzonic says it installs Python
  dependencies automatically; if the console asks for a build command, use
  `pip install -r requirements.txt`.
- Entry point: `main:app`. The shim at `backend/main.py` re-exports `app` from `app.main` so the
  detector can find it at the configured root. For Enzonic's function deployment, let the
  platform's FastAPI adapter handle requests; only use a Uvicorn command if the dashboard/logs
  specifically say it selected server mode and asks for a server start command.

The repository push should create a new build/preview if this branch is connected. Wait for the
build to reach **Ready**. If the console associates the branch preview with a different URL,
copy that exact URL; the project's production alias may remain unassigned until a ready build is
promoted. Do not assume the original `.enzonic.dev` URL works merely because it appears in the
project settings. If the build still fails, share the new final error lines (not tokens or private
environment values).

## Verify and build the phone APK

After Enzonic reports a ready deployment, open its actual HTTPS deployment URL plus `/health`;
expected JSON contains `"status":"ok"`. Then manually run GitHub Actions → **Android build** →
**Run workflow** and pass the exact HTTPS root URL. The workflow runs `scripts/smoke_live_api.py`
before producing an APK, checking:

- `/health` and `/api/providers`
- market candles and gold analysis (requires reachable Yahoo Finance)
- signal history after analysis
- risk calculation
- screenshot upload using a generated PNG
- wildcard CORS preflight

A smoke-test failure stops the build rather than publishing an APK as verified. The APK URL is
embedded as the Android default, while the saved Advanced connection setting remains available.
The screenshot endpoint only validates/samples pixels and always returns `WAIT`; it is not a
vision model. Market data can be delayed or unavailable. The backend is unauthenticated, so do
not expose secrets through it.

## Data and free-tier caveats

No API keys are required for the current backend. Never commit `.env`, credentials, or access
tokens. CORS is wildcard without credentials. SQLite signal history is local storage; Enzonic
Deploy function/server filesystem persistence across deployments or instances is not guaranteed.
Do not treat it as durable until verified. Free functions/server behavior and quotas can change;
avoid paid upgrades unless the owner explicitly approves them.
