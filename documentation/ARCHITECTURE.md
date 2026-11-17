# Architecture — AI Trading Analyst

## Overview

```
┌─────────────┐      HTTPS/JSON      ┌──────────────┐
│ React + TS  │ ◄──────────────────► │ FastAPI      │
│ (frontend/) │   (Vite dev proxy)   │ (backend/)   │
└─────────────┘                      └──────┬───────┘
                                          │
        ┌─────────────┬─────────────┬─────┴──────┬─────────────┐
        ▼             ▼             ▼            ▼             ▼
   market_data   indicators    signals    backtesting   screenshot
   (providers,   (EMA/RSI/     (BUY/SELL/  (history,     (OpenCV,
    CSV, cache)   MACD/ATR…)   WAIT, MTF)   metrics)      patterns)
                                          │
                                    SQLite (signals,
                                    candles cache)
```

## Design principles

1. **Deterministic core, optional AI.** All numbers come from tested math (indicators,
   structure, S/R). The explanation layer turns those numbers into sentences; it never
   invents data. External AI (if ever added) only *explains*, never overrides.
2. **No fabricated data.** Every price shown carries source + timestamp. Demo data is
   labelled as such and never presented as live.
3. **Free-first.** Python/FastAPI/SQLite, React/TS, OpenCV, free market-data sources.
   No paid services; secrets via `.env`.
4. **Modular.** Each engine (indicators, strategies, signals, backtesting) is an
   independent package with its own tests, so phases can land incrementally.
5. **WAIT is a first-class outcome.** Conflicting timeframes or weak evidence ⇒ WAIT.

## Backend (`backend/`)

- `app/main.py` — FastAPI app, CORS, `/health`, `/`
- `app/core/config.py` — pydantic-settings from environment
- `app/api/` — route modules (added per phase)
- `indicators/`, `strategies/`, `signals/`, `market_data/`, `backtesting/` — engines
- `tests/` — pytest suite

## Frontend (`frontend/`)

- Vite + React 18 + TypeScript, dark financial-terminal theme
- 14 sections per spec; each section is wired to real API endpoints as phases land
- TradingView Lightweight Charts will be added in Phase 2/7 for candlestick rendering

## Android (`android/`)

- Kotlin + Jetpack Compose (Phase 8), consuming the same FastAPI backend

## Data flow (Mode A)

Provider/CSV → validation → cache (SQLite) → indicators → structure/S/R →
multi-timeframe fusion → signal + explanation → signal history → UI.

## Data flow (Mode B)

Upload → validation (type/size) → OpenCV preprocessing → candle/structure extraction →
evidence lists (bullish/bearish) → assessment + missing-info report → UI.
