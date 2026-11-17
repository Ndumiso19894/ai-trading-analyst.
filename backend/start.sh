#!/usr/bin/env sh
# Enzonic panel startup command: bash start.sh (set the working directory to backend).
set -eu
exec uvicorn app.main:app --host 0.0.0.0 --port "${PORT:-8000}"
