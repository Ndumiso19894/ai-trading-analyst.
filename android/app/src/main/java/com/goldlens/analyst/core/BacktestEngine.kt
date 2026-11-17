package com.goldlens.analyst.core

import kotlin.math.abs
import kotlin.math.max

/** User-entered price-unit costs; zero must be chosen explicitly and is not claimed to be broker cost. */
data class BacktestCosts(
    val spreadPriceUnits: Double,
    val slippagePriceUnits: Double,
    val commissionPriceUnitsPerSide: Double,
) {
    init {
        require(listOf(spreadPriceUnits, slippagePriceUnits, commissionPriceUnitsPerSide).all { it.isFinite() && it >= 0.0 }) {
            "Backtest costs must be non-negative finite price-unit assumptions."
        }
    }
}

data class BacktestTrade(
    val side: TradeSide,
    val entryIndex: Int,
    val exitIndex: Int,
    val entry: Double,
    val exit: Double,
    val netPnlPerUnit: Double,
    val riskDistance: Double,
    val outcome: String,
)

data class BacktestMetrics(
    val totalTrades: Int,
    val wins: Int,
    val losses: Int,
    val winRatePercent: Double?,
    val profitFactor: Double?,
    val netPnlPerUnit: Double,
    val maxDrawdownPerUnit: Double,
    val averageRiskReward: Double?,
    val longestLosingStreak: Int,
    val equityCurve: List<Double>,
    val trades: List<BacktestTrade>,
)

data class BacktestReport(
    val instrument: String,
    val timeframe: String,
    val sourceLabel: String,
    val sourcePrecision: Int?,
    val barCount: Int,
    val splitIndex: Int,
    val costs: BacktestCosts,
    val full: BacktestMetrics,
    val inSample: BacktestMetrics,
    val outOfSample: BacktestMetrics,
    val assumptions: List<String>,
)

/** Causal EMA 20/50 crossover baseline. This is a historical simulation, not a forecast. */
object BacktestEngine {
    private const val WARMUP_BARS = 220
    private const val STOP_ATR_MULTIPLE = 1.5
    private const val TARGET_ATR_MULTIPLE = 3.0

    private data class Position(
        val side: TradeSide,
        val entryIndex: Int,
        val entry: Double,
        val stop: Double,
        val target: Double,
        val riskDistance: Double,
    )

    fun run(dataset: MarketDataset, costs: BacktestCosts): BacktestReport {
        val candles = normalizeCandles(dataset.candles)
        require(candles.size >= WARMUP_BARS + 20) { "At least ${WARMUP_BARS + 20} valid bars are required for this baseline strategy." }
        val indicators = TechnicalIndicators.calculate(candles)
        val split = (candles.size * 0.7).toInt().coerceIn(WARMUP_BARS, candles.lastIndex)
        val fullTrades = simulate(candles, indicators, costs, WARMUP_BARS, candles.size)
        val trainTrades = simulate(candles, indicators, costs, WARMUP_BARS, split)
        val testTrades = simulate(candles, indicators, costs, split, candles.size)
        return BacktestReport(
            instrument = dataset.instrument,
            timeframe = dataset.timeframe,
            sourceLabel = dataset.sourceLabel,
            sourcePrecision = dataset.sourcePrecision,
            barCount = candles.size,
            splitIndex = split,
            costs = costs,
            full = metrics(fullTrades),
            inSample = metrics(trainTrades),
            outOfSample = metrics(testTrades),
            assumptions = listOf(
                "EMA(20/50) crossover is confirmed on a completed candle; entry fills at the next candle open.",
                "Stop is 1.5 prior-bar ATR; target is 3 prior-bar ATR.",
                "If stop and target are both touched in one candle, stop is assumed first.",
                "A position exited inside a candle cannot be re-entered on that same candle.",
                "Spread is deducted once per round trip; slippage and commission are user-supplied price-unit amounts deducted per side.",
                "Stop/target checks include the entry candle; stop wins if both are touched, and gap-through stops fill at the worse candle open.",
                "Open positions at the end of a test segment are excluded from closed-trade metrics.",
                "Chronological split is 70/30; no account currency conversion, financing, lot size or real broker fills are modelled.",
            ),
        )
    }

    private fun simulate(
        candles: List<Candle>,
        indicators: List<IndicatorValues>,
        costs: BacktestCosts,
        startIndex: Int,
        endExclusive: Int,
    ): List<BacktestTrade> {
        val trades = mutableListOf<BacktestTrade>()
        var position: Position? = null
        val lastIndex = endExclusive.coerceAtMost(candles.size)
        for (i in max(WARMUP_BARS, startIndex).coerceAtLeast(1) until lastIndex) {
            val openAtStart = position
            var exitedThisBar = false
            val bar = candles[i]
            if (openAtStart != null) {
                val stopHit = when (openAtStart.side) {
                    TradeSide.LONG -> bar.low <= openAtStart.stop
                    TradeSide.SHORT -> bar.high >= openAtStart.stop
                }
                val targetHit = when (openAtStart.side) {
                    TradeSide.LONG -> bar.high >= openAtStart.target
                    TradeSide.SHORT -> bar.low <= openAtStart.target
                }
                val exit = resolveExit(openAtStart, bar, stopHit, targetHit)
                if (exit != null) {
                    trades += closeTrade(openAtStart, i, exit, costs)
                    position = null
                    exitedThisBar = true
                }
            }
            if (position == null && !exitedThisBar && i >= 2) {
                val older = indicators[i - 2]
                val previous = indicators[i - 1]
                val currentAtr = previous.atr14
                val upCross = older.ema20 != null && older.ema50 != null && previous.ema20 != null && previous.ema50 != null &&
                    older.ema20 <= older.ema50 && previous.ema20 > previous.ema50
                val downCross = older.ema20 != null && older.ema50 != null && previous.ema20 != null && previous.ema50 != null &&
                    older.ema20 >= older.ema50 && previous.ema20 < previous.ema50
                if (currentAtr != null && currentAtr.isFinite() && currentAtr > 0.0 && (upCross || downCross)) {
                    val side = if (upCross) TradeSide.LONG else TradeSide.SHORT
                    val entry = bar.open
                    val risk = STOP_ATR_MULTIPLE * currentAtr
                    val opened = Position(
                        side, i, entry,
                        if (side == TradeSide.LONG) entry - risk else entry + risk,
                        if (side == TradeSide.LONG) entry + TARGET_ATR_MULTIPLE * currentAtr else entry - TARGET_ATR_MULTIPLE * currentAtr,
                        risk,
                    )
                    position = opened
                    val stopHit = if (side == TradeSide.LONG) bar.low <= opened.stop else bar.high >= opened.stop
                    val targetHit = if (side == TradeSide.LONG) bar.high >= opened.target else bar.low <= opened.target
                    val sameBarExit = resolveExit(opened, bar, stopHit, targetHit)
                    if (sameBarExit != null) {
                        trades += closeTrade(opened, i, sameBarExit, costs)
                        position = null
                    }
                }
            }
        }
        return trades
    }

    /** Stop wins a same-bar stop/target collision; gap-through stops fill at the worse bar open. */
    private fun resolveExit(position: Position, bar: Candle, stopHit: Boolean, targetHit: Boolean): Double? = when {
        stopHit -> when (position.side) {
            TradeSide.LONG -> minOf(position.stop, bar.open)
            TradeSide.SHORT -> maxOf(position.stop, bar.open)
        }
        targetHit -> when (position.side) {
            TradeSide.LONG -> maxOf(position.target, bar.open)
            TradeSide.SHORT -> minOf(position.target, bar.open)
        }
        else -> null
    }

    private fun closeTrade(position: Position, exitIndex: Int, exit: Double, costs: BacktestCosts): BacktestTrade {
        val gross = (exit - position.entry) * if (position.side == TradeSide.LONG) 1.0 else -1.0
        val roundTripCosts = costs.spreadPriceUnits + 2.0 * costs.slippagePriceUnits + 2.0 * costs.commissionPriceUnitsPerSide
        val net = gross - roundTripCosts
        return BacktestTrade(
            position.side, position.entryIndex, exitIndex, position.entry, exit, net, position.riskDistance,
            if (net > 0.0) "win" else "loss",
        )
    }

    private fun metrics(trades: List<BacktestTrade>): BacktestMetrics {
        val wins = trades.filter { it.netPnlPerUnit > 0.0 }
        val losses = trades.filter { it.netPnlPerUnit <= 0.0 }
        val equity = mutableListOf<Double>()
        var balance = 0.0
        var peak = 0.0
        var drawdown = 0.0
        var losingStreak = 0
        var longestLosingStreak = 0
        trades.forEach { trade ->
            balance += trade.netPnlPerUnit
            peak = max(peak, balance)
            drawdown = max(drawdown, peak - balance)
            equity += balance
            if (trade.netPnlPerUnit <= 0.0) {
                losingStreak++
                longestLosingStreak = max(longestLosingStreak, losingStreak)
            } else losingStreak = 0
        }
        val grossWin = wins.sumOf(BacktestTrade::netPnlPerUnit)
        val grossLoss = abs(losses.sumOf(BacktestTrade::netPnlPerUnit))
        val riskRewards = trades.map { it.netPnlPerUnit / max(it.riskDistance, 1e-12) }
        return BacktestMetrics(
            totalTrades = trades.size,
            wins = wins.size,
            losses = losses.size,
            winRatePercent = if (trades.isEmpty()) null else wins.size * 100.0 / trades.size,
            profitFactor = when {
                grossLoss > 0.0 -> grossWin / grossLoss
                grossWin > 0.0 -> Double.POSITIVE_INFINITY
                else -> null
            },
            netPnlPerUnit = trades.sumOf(BacktestTrade::netPnlPerUnit),
            maxDrawdownPerUnit = drawdown,
            averageRiskReward = riskRewards.takeIf { it.isNotEmpty() }?.average(),
            longestLosingStreak = longestLosingStreak,
            equityCurve = equity,
            trades = trades,
        )
    }
}
