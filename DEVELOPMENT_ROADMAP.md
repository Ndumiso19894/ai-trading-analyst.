# Development Roadmap — AI Trading Analyst

Phased plan. Each phase ends with tests + commit + push. No phase is "done" until
verified against the acceptance criteria.

## Phase 1 — Project foundation ✅ (foundation delivered; private repository setup pending)
- [x] Inspect environment (Python 3.11, Node 22, GitHub connected)
- [x] Create project structure (backend / frontend / android / documentation / datasets / scripts)
- [x] FastAPI backend skeleton with `/health` + CORS + env config
- [x] React + TS frontend skeleton with 14-section nav + backend status check
- [x] `.gitignore`, `.env.example`, docs (README, ARCHITECTURE, CHANGELOG, TESTING)
- [x] Verify backend tests pass (2/2); verify frontend production build
- [x] Commit + push foundation to the current Arena branch
- [ ] Rename existing GitHub repository from `ai-trading-analyst.` to `ai-trading-analyst` and make it private (blocked by GitHub integration permissions; owner action required)

## Phase 2 — Market data
- Research free XAU/USD sources (rate limits, licensing, history depth, delay vs real-time)
- Implement provider(s) + CSV upload, OHLC validation, caching, dedupe, failure handling
- Display source + timestamp; never fabricate prices

## Phase 3 — Technical analysis
- Indicators: EMA 20/50/100/200, RSI 14, MACD, Stochastic, ATR 14, Bollinger Bands
- Price action: structure (HH/HL/LH/LL), BOS/CHoCH, breakouts, pullbacks, rejections
- Support/resistance + supply/demand zones with strength scoring
- Unit tests against known values

## Phase 4 — Signal engine
- BUY/SELL/WAIT with entry, SL, TP1, TP2, R:R, invalidation, supporting/conflicting evidence
- Multi-timeframe agreement/conflict logic; deterministic explanation engine
- Signal history persistence (SQLite)

## Phase 5 — Screenshot analysis
- Upload (PNG/JPEG), OpenCV preprocessing, candle/structure extraction
- Direction, patterns, S/R, breakouts/reversals; BUY/SELL/WAIT + missing-info report
- Safeguards: no invented prices/readings/timeframes

## Phase 6 — Backtesting
- Historical strategy testing with spread/slippage/costs, no look-ahead bias
- Metrics: win rate, profit factor, max drawdown, expectancy, per-timeframe breakdown
- Out-of-sample support; performance charts

## Phase 7 — Complete web application
- Integrate all 14 sections; risk calculator (1:1, 1:1.5, 1:2, 1:3); notifications (local)
- End-to-end test; install/usage docs; stable release tag

## Phase 8 — Android application
- Kotlin + Jetpack Compose; reuse backend API
- Chart viewing, screenshot upload, signals, history; build APK

## Future (after Gold is reliable)
EUR/USD, GBP/USD, USD/JPY, AUD/USD, other instruments with free data.
