# Enzonic Free Python Hosting

## Status — not yet provisioned or publicly verified

Render has been abandoned and is not part of this deployment plan. No Enzonic account has been
created or accessed in this session, no server has been provisioned, and there is no verified
public URL yet. The actual hostname must come from the Enzonic panel after the free Python
server is created; do not use an example hostname as a real URL.

## Official plan facts checked

The official [Enzonic Python Hosting page](https://www.enzonic.com/python) currently says its
Python **server hosting** Free Forever tier is one server per account, subject to capacity,
with **512 MB RAM, 50% CPU, 10 GB storage, 24/7 uptime**, and **no card/payment details**.
It lists Python 3.10, 3.11 and 3.12, automatic `requirements.txt` installation, GitHub repository
deployment, and custom Uvicorn/Gunicorn/Hypercorn startup commands. The page specifically
links **Deploy your app** to the panel server product, which is the product this guide selects.

Important: Enzonic's separate **Enzonic Deploy** product is not the requested server. Its own
free tier has a different quota and server mode can sleep after 15 minutes; do not select it
for this request. The official Python page currently contains a live/indexed-content discrepancy:
the live page fetched for this task says 512 MB/50% CPU/10 GB, while a current search-engine
snippet for the same URL says 1 GB/100% CPU/5 GB. The free/no-card/24-7 claims are stated on
the live page, but the precise resources should be checked in the panel before provisioning.
If the panel offers only a paid option, asks for payment details, or has no eligible free slot,
stop without selecting/activating it and report that blocker. Free availability is explicitly
subject to capacity; this session cannot verify the owner's account eligibility without the
owner completing Enzonic sign-in.

The 512 MB spec in the live page is tight for NumPy, Pandas, market-data parsing and analysis.
The app is prepared to run on it, but peak memory and upstream Yahoo access must be verified
on the actual host. No paid upgrade is recommended or authorized.

## Prepare the Free Python server from an Android phone

1. Open [Enzonic Python Hosting](https://www.enzonic.com/python) and tap **Deploy your app**.
   This opens the Enzonic panel server flow, not Enzonic Deploy and not a VPS checkout.
2. Sign in/create the owner's Enzonic account. The public Python-hosting page says no card is
   required; do not enter payment information. If the selected free Python server is unavailable
   or the panel requires payment/card details, stop and tell me what the panel says.
3. Select the **Free** Python/generic Python server option only. Before final confirmation,
   check the plan and resources shown in the panel. Don't accept an upgrade, paid add-on or
   trial that rolls into billing.
4. Connect the existing GitHub repository `Ndumiso19894/ai-trading-analyst.` using Enzonic's
   repository integration. Select branch `arena/8a1d0765-ai-trading-analyst` and use
   `backend/` as the working/root directory. The root contains `requirements.txt` and
   `start.sh`. The repository is public; if the panel needs a GitHub App authorization, approve
   only the requested repository access in GitHub's own screen. Never send credentials, tokens
   or recovery codes in chat.
5. Let the host install `requirements.txt`. Set the startup command to `./start.sh` (or
   `bash start.sh`) from the `backend/` working directory. It runs
   `uvicorn app.main:app --host 0.0.0.0 --port "${PORT:-8000}"`, using the assigned port when
   supplied and port 8000 otherwise. Python's backend supports 3.10–3.12.
6. No API secrets are needed. The app reads configuration from environment variables and
   `.env` is not committed. Keep `CORS_ORIGINS=*` unless a browser frontend origin is later
   explicitly restricted. Native Android networking uses HTTPS and is not governed by browser
   CORS.
7. Start the server and find the panel's actual public domain/HTTPS address and assigned port
   or proxy mapping. Check `https://<actual-host>/health`. A panel server address must route
   external HTTPS traffic to the Uvicorn port; if the free template does not provide inbound
   web traffic/HTTPS or an address, stop and report that unsupported capability. Do not invent
   a URL or expose an arbitrary port without panel support.

## Endpoint verification after provisioning

The smoke suite in `scripts/smoke_live_api.py` is also run automatically when GitHub Actions is
manually dispatched with the actual URL. It checks:

- `/health`
- `/api/providers`
- `/api/market/candles?timeframe=1h&period=5d`
- `/api/analysis?timeframe=1h&period=3mo`
- `/api/signals` after analysis
- `POST /api/risk/calculate`
- `POST /api/screenshot/analyze` with a generated PNG
- credential-free wildcard CORS preflight

Market and analysis checks depend on Yahoo Finance being reachable from Enzonic. They fail
rather than claim a successful live quote if the upstream provider blocks the host. Screenshot
analysis validates and samples the image but always returns `WAIT`; it is not a vision model.
The tests do not place trades.

The Android APK workflow's **Run workflow** form requires the verified public HTTPS URL,
smoke-tests the live API, and only then embeds that URL and builds the debug/release APKs. It
is deliberately manual-only while no verified address exists, so no unconfigured APK artifact
is presented as a ready-to-use phone release. The app retains its advanced on-device URL setting
for manual fallback.

## Data, security and free-tier caveats

- Do not commit `.env`, credentials, or API tokens. The current application needs no provider
  keys. Use Enzonic's private environment-variable panel for any owner-managed values.
- Signal history is SQLite at `backend/trading.sqlite3` by default. Confirm the panel's storage
  persistence and backup behavior; history may be lost if the server filesystem is reset.
- Yahoo's chart URL is unofficial, delayed/provider-dependent and has no SLA.
- The live Python page advertises always-on 24/7 Python server hosting without sleep. Check the
  actual free server's control panel and current limits; this must not be confused with Enzonic
  Deploy's separate free server-mode sleep behavior.
- The advertised free resources are small. If installation/runtime exceeds the free memory,
  do not upgrade automatically; report the measured failure and let the owner decide.
