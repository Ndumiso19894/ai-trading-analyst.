# Architecture

## Runtime topology

```
React + TypeScript (Vite) --relative /api via proxy--> FastAPI
                                                     ├─ provider adapter (Yahoo chart)
                                                     ├─ CSV OHLCV import
                                                     ├─ candle validation + bounded RAM TTL cache
                                                     ├─ indicators -> structure/SR -> signal/explanation
                                                     ├─ chronological CSV backtester
                                                     ├─ local Pillow screenshot sampling (abstains)
                                                     └─ SQLite signal history/outcome counts

Android Compose client ------------------------------> same FastAPI API
```

## Backend modules

- `backend/app/main.py`: FastAPI service, health endpoint, API router, CORS.
- `backend/app/api/routes.py`: market, CSV, analysis, multi-timeframe, risk, backtest,
  screenshot, history and outcome endpoints.
- `backend/market_data/providers.py`: async Yahoo chart endpoint; keyless but unofficial.
- `backend/market_data/models.py`: schema validation, normalization and resampling.
- `backend/market_data/cache.py`: bounded per-process 60-second cache.
- `backend/indicators/calculations.py`: pandas/numpy indicator math; warm-up values are NaN.
- `backend/signals/analysis.py`: evidence aggregation, swing pivots, support/resistance,
  deterministic natural-language explanation. WAIT on weak/conflicting evidence.
- `backend/signals/storage.py`: local SQLite history, outcome state, summary counts.
- `backend/backtesting/engine.py`: causal EMA20/50 crossover test, ATR exits, costs and
  stop-first ambiguous bar processing.

## Market-data constraints

Ticker is fixed to Yahoo's `XAUUSD=X`. Endpoint access and ticker availability depend on
provider. Market values are labelled with source and observation timestamp; delayed quote
possibility is exposed. No live price is invented. 4-hour bars aggregate hourly data;
other timeframes are requested from provider. No lower-interval generation occurs when
there are not enough source candles. CSV is the offline path.

## Signal logic

EMA alignment/price, RSI range, MACD histogram and recent swing structure contribute
signed evidence. A direction requires multiple aligned checks and ATR to construct levels;
otherwise the result abstains. Live analysis checks higher frames (daily/4H for 1H, 1H/daily for lower frames) and
forces WAIT when those frames conflict or cannot be retrieved. A single-timeframe CSV is
marked as not MTF-checked. Stop distance = 1.5 ATR; TP1 = 1.5 ATR and TP2 = 3 ATR, so
nominal R:R is 1:1 and 1:2. These are rules, not calibrated probabilities. Indicators are
not statistically independent, and no confidence score is produced.

## Backtest limitations

EMA-cross entry is decided on a completed candle and filled at the next bar's open. ATR
stop/target derive from prior completed data; spread/slippage assumptions are deducted.
If stop and target occur in a single bar, stop is assumed first. Split is chronological
70/30 and the out-of-sample pass uses pre-split history for warm-up but only evaluates
post-split entries. Results are per-unit price P&L, not account return; contract size,
financing, currency conversion, variable spreads and real execution are not represented.

## Screenshot path

Pillow decodes a bounded PNG/JPEG, downsamples, counts rough greenish/reddish pixels and
returns WAIT. It cannot identify the chart, candlesticks, levels, instrument, timeframe,
prices, or indicator values. No AI/vision model is installed. This API must not be described
as reliable screenshot analysis.

## Android

`android/` contains a native Compose client source using `HttpURLConnection`; emulator
backend default is `10.0.2.2:8000`. Build not verified: development environment lacks JDK,
Android SDK and Gradle. Cleartext HTTP is development-only.
