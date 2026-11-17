# GoldLens architecture and trust boundaries

## Component map

```text
Android Kotlin/Compose app
  ├─ InstrumentRegistry -> selected user-imported OHLC data
  ├─ CsvCandleParser -> validation/import/export
  ├─ AnalysisEngine -> TechnicalIndicators + confirmed-pivot support/resistance
  ├─ RiskInput.calculate -> explicit broker contract/tick data
  ├─ Room -> candles, analyses/zones, manual journal, settings, backtest/alert records
  └─ FloatingAssistantService -> user-started overlay over other apps

React/TypeScript dashboard --relative API paths--> optional FastAPI backend
  ├─ Yahoo Finance chart adapter (unofficial; fixed XAU/USD)
  ├─ CSV validation/resampling
  ├─ pandas indicators, signal logic and heuristic levels
  ├─ CSV EMA-cross backtester and SQLite signal history
  └─ bounded screenshot image check/color sampler that returns WAIT
```

Android local calculations do not require the backend. The Android app does not currently use the Yahoo provider or present its data as an Android quote feed. Web/backend features and Android features must be described separately.

## Android local data model

Room's v1 schema stores instrument symbol/timeframe/timestamp-keyed candle rows, analysis records and zones, manual trades, settings, backtest record placeholders and alert-rule records. Current changes preserve existing table schemas. There is not yet a migration chain, database export/restore, or sync queue. The selected instrument catalog contains the requested 11 FX pairs and three precious metals; broker aliases and contract values are unverified templates, not exchange/broker facts.

CSV import accepts common OHLC column names and timestamp/date forms, uses UTC for timestamps without a zone, validates OHLC bounds, orders rows and resolves duplicate timestamps. It does not currently handle decimal-comma input, configurable timezone conversion, preview-before-save, or symbol extraction from file metadata. Export writes sorted UTC ISO-8601 timestamps and locale-independent numeric values.

## Analysis and source quality

Kotlin indicator math is tested against the Python reference fixture. EMA uses recursive `adjust=False` semantics; RSI and ATR use Wilder alpha `1/period`; Bollinger standard deviation uses population variance; stochastic uses rolling high/low. Warm-up values remain unavailable. Confirmed pivots require right-side bars from the supplied historical prefix. Zone scores are heuristics, not probabilities. Android currently analyzes a single selected timeframe; it does not yet aggregate distinct datasets for higher-timeframe confirmation.

A data source and label accompany analysis. In the current Android path the supported data is imported or locally saved CSV. Live/delayed enum variants have stale-data safeguards for future adapters, but no Android live provider is connected. The backend Yahoo adapter is unofficial, XAU/USD-only through API routes, may be delayed or unavailable, and has no real-time/SLA guarantee. No sources should be silently combined.

## Optional backend

The backend provider module is isolated from its API routes and can be replaced later. Current routes restrict market candles/analysis to Yahoo's `XAUUSD=X`; 4H data is resampled from hourly data. The React dashboard is an optional backend client. Backend local SQLite signal history does not constitute Android Room sync or cloud persistence.

The Python EMA-cross backtest enters at next-bar open after prior-bar confirmation, uses ATR stop/target, applies user cost assumptions, resolves same-bar stop/target collisions stop-first, and separates chronological in-sample/out-of-sample evaluation. Results are per-unit, not account returns; no variable broker spread, financing, contract conversion or real fill model is included.

## Overlay and screenshot boundaries

The overlay requires Android's explicit overlay permission and user-started foreground service. It analyzes locally stored candles and does not inspect other applications. It does not capture the display, read MT5 internals, submit orders, or claim broker OHLC.

There is no Android MediaProjection workflow or validated chart OCR/vision. The optional backend screenshot endpoint decodes a bounded user-uploaded image, samples rough colors and abstains with WAIT. No exact prices/instrument/timeframe/structure are extracted.

## Unimplemented cloud/background systems

No Google OAuth/Drive backup, GitHub snapshot sync, unified sync manager, WorkManager alerts or continuous background quote polling is implemented. Android periodic work, if added, must respect OS scheduling limits; no uninterrupted monitoring guarantee should be made.
