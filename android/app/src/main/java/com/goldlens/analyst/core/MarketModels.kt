package com.goldlens.analyst.core

import kotlin.math.abs

/** One OHLCV observation. timestampMillis is UTC epoch milliseconds. */
data class Candle(
    val timestampMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 0.0,
) {
    fun isValid(): Boolean = timestampMillis > 0 &&
        listOf(open, high, low, close, volume).all { it.isFinite() } &&
        open > 0 && high > 0 && low > 0 && close > 0 && volume >= 0 &&
        high >= maxOf(open, close, low) && low <= minOf(open, close, high)
}

enum class DataSourceKind { LIVE, DELAYED, HISTORICAL, IMPORTED_CSV }
enum class Direction { BUY, SELL, WAIT }
enum class ZoneSide { SUPPORT, RESISTANCE }
enum class ZoneStatus { ACTIVE, BROKEN, RETESTING }

data class MarketDataset(
    val candles: List<Candle>,
    val source: DataSourceKind,
    val sourceLabel: String,
    val timeframe: String,
    val loadedAtMillis: Long,
    val instrument: String = "XAU/USD",
) {
    fun normalized(): MarketDataset = copy(candles = normalizeCandles(candles))
}

/** Sorts by UTC time and keeps the last valid row for duplicate timestamps. */
fun normalizeCandles(input: List<Candle>): List<Candle> {
    val valid = input.filter(Candle::isValid).withIndex()
        .sortedWith(compareBy<IndexedValue<Candle>> { it.value.timestampMillis }.thenBy { it.index })
    val byTimestamp = linkedMapOf<Long, Candle>()
    valid.forEach { byTimestamp[it.value.timestampMillis] = it.value }
    require(byTimestamp.isNotEmpty()) { "No valid OHLC candles were found." }
    return byTimestamp.values.toList()
}

data class IndicatorValues(
    val ema20: Double? = null,
    val ema50: Double? = null,
    val ema100: Double? = null,
    val ema200: Double? = null,
    val rsi14: Double? = null,
    val macd: Double? = null,
    val macdSignal: Double? = null,
    val macdHistogram: Double? = null,
    val atr14: Double? = null,
    val bollingerMiddle: Double? = null,
    val bollingerUpper: Double? = null,
    val bollingerLower: Double? = null,
    val stochasticK: Double? = null,
    val stochasticD: Double? = null,
)

data class SwingStructure(
    val trend: String,
    val swingHighs: List<Double>,
    val swingLows: List<Double>,
)

data class PriceZone(
    val side: ZoneSide,
    val lower: Double,
    val upper: Double,
    val midpoint: Double,
    val touches: Int,
    val rejectionScore: Double,
    val strength: String,
    val timeframe: String,
    val lastTouchMillis: Long,
    val status: ZoneStatus,
    val confirmingTimeframes: List<String> = listOf(timeframe),
)

data class AnalysisResult(
    val direction: Direction,
    val price: Double?,
    val timestampMillis: Long?,
    val timeframe: String,
    val dataSource: DataSourceKind,
    val score: Int,
    val reason: String,
    val evidence: List<String>,
    val conflicts: List<String>,
    val indicators: IndicatorValues?,
    val structure: SwingStructure?,
    val zones: List<PriceZone>,
    val entry: Double?,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val rewardRisk: Double?,
    val instrument: String = "XAU/USD",
)

data class RiskInput(
    val accountBalance: Double,
    val riskPercent: Double,
    val entry: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val contractSize: Double? = null,
    val tickSize: Double? = null,
    val tickValuePerLot: Double? = null,
)

data class RiskResult(
    val riskAmount: Double,
    val riskPercent: Double,
    val riskPerLot: Double,
    val positionLots: Double,
    val potentialLoss: Double,
    val potentialProfit: Double,
    val rewardRisk: Double,
    val warning: String = "Estimate only. Verify contract size, tick value, account currency conversion, spread and fees with your broker.",
)

fun RiskInput.calculate(): RiskResult {
    require(accountBalance.isFinite() && accountBalance > 0) { "Account balance must be positive." }
    require(riskPercent.isFinite() && riskPercent > 0 && riskPercent <= 5) { "Risk percentage must be greater than 0 and no more than 5%." }
    require(entry.isFinite() && entry > 0 && stopLoss.isFinite() && stopLoss > 0 && takeProfit.isFinite() && takeProfit > 0) { "Prices must be positive finite numbers." }
    require(abs(entry - stopLoss) > 0) { "Entry and stop loss must be different." }
    require((tickSize == null) == (tickValuePerLot == null)) { "Enter both tick size and tick value, or leave both blank." }
    val tick = tickSize
    val tickValue = tickValuePerLot
    if (tick != null && tickValue != null) require(tick.isFinite() && tick > 0 && tickValue.isFinite() && tickValue > 0) { "Tick size and tick value must be positive." }
    if (tick == null || tickValue == null) require(contractSize != null && contractSize.isFinite() && contractSize > 0) { "Enter a verified broker contract size, or both tick size and tick value." }
    val riskMoney = accountBalance * riskPercent / 100.0
    val stopDistance = abs(entry - stopLoss)
    val targetDistance = abs(takeProfit - entry)
    val riskPerLot = if (tick != null && tickValue != null) stopDistance / tick * tickValue else stopDistance * contractSize!!
    require(riskPerLot.isFinite() && riskPerLot > 0) { "Could not calculate risk per lot." }
    val lots = riskMoney / riskPerLot
    val profitPerLot = if (tick != null && tickValue != null) targetDistance / tick * tickValue else targetDistance * contractSize!!
    return RiskResult(riskMoney, riskPercent, riskPerLot, lots, riskMoney, lots * profitPerLot, targetDistance / stopDistance)
}
