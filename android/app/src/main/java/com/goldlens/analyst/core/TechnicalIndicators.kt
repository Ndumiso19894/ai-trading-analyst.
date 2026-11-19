package com.goldlens.analyst.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure Kotlin indicator calculations designed to match backend/indicators/calculations.py.
 * EMA uses the same recursive first-value seed, alpha and warm-up visibility as pandas ewm
 * with adjust=False and min_periods=period. No future candles are read.
 */
object TechnicalIndicators {
    fun calculate(candles: List<Candle>): List<IndicatorValues> {
        if (candles.isEmpty()) return emptyList()
        val close = candles.map(Candle::close)
        val high = candles.map(Candle::high)
        val low = candles.map(Candle::low)
        val ema20 = ema(close.map { it }, 20)
        val ema50 = ema(close.map { it }, 50)
        val ema100 = ema(close.map { it }, 100)
        val ema200 = ema(close.map { it }, 200)

        val changes = close.indices.map { i -> if (i == 0) null else close[i] - close[i - 1] }
        val gains = changes.map { it?.coerceAtLeast(0.0) }
        val losses = changes.map { it?.let { value -> (-value).coerceAtLeast(0.0) } }
        val avgGain = wilderSmooth(gains, 14)
        val avgLoss = wilderSmooth(losses, 14)
        val rsi = close.indices.map { i ->
            val gain = avgGain[i]
            val loss = avgLoss[i]
            when {
                gain == null || loss == null -> null
                loss == 0.0 && gain > 0.0 -> 100.0
                gain == 0.0 && loss > 0.0 -> 0.0
                loss == 0.0 && gain == 0.0 -> null
                else -> 100.0 - 100.0 / (1.0 + gain / loss)
            }
        }

        val ema12 = ema(close, 12)
        val ema26 = ema(close, 26)
        val macd = close.indices.map { i ->
            val fast = ema12[i]
            val slow = ema26[i]
            if (fast == null || slow == null) null else fast - slow
        }
        val macdSignal = ewmNullable(macd, 9)
        val macdHistogram = macd.indices.map { i ->
            val m = macd[i]
            val s = macdSignal[i]
            if (m == null || s == null) null else m - s
        }

        val trueRange = candles.indices.map { i ->
            if (i == 0) high[i] - low[i]
            else max(high[i] - low[i], max(kotlin.math.abs(high[i] - close[i - 1]), kotlin.math.abs(low[i] - close[i - 1])))
        }
        val atr = ewmNullable(trueRange.map { it }, 14, smoothingAlpha = 1.0 / 14.0)

        val bbMiddle = rollingMean(close.map { it }, 20)
        val bbStd = rollingPopulationStd(close, 20)
        val bbUpper = close.indices.map { i ->
            val middle = bbMiddle[i]
            val std = bbStd[i]
            if (middle == null || std == null) null else middle + 2.0 * std
        }
        val bbLower = close.indices.map { i ->
            val middle = bbMiddle[i]
            val std = bbStd[i]
            if (middle == null || std == null) null else middle - 2.0 * std
        }

        val stochK = candles.indices.map { i ->
            if (i + 1 < 14) null else {
                val lowest = low.subList(i - 13, i + 1).minOrNull()!!
                val highest = high.subList(i - 13, i + 1).maxOrNull()!!
                val denominator = highest - lowest
                if (denominator == 0.0) null else 100.0 * (close[i] - lowest) / denominator
            }
        }
        val stochD = rollingMeanNullable(stochK, 3)

        return candles.indices.map { i ->
            IndicatorValues(
                ema20 = ema20[i], ema50 = ema50[i], ema100 = ema100[i], ema200 = ema200[i],
                rsi14 = rsi[i], macd = macd[i], macdSignal = macdSignal[i], macdHistogram = macdHistogram[i],
                atr14 = atr[i], bollingerMiddle = bbMiddle[i], bollingerUpper = bbUpper[i], bollingerLower = bbLower[i],
                stochasticK = stochK[i], stochasticD = stochD[i],
            )
        }
    }

    private fun ema(values: List<Double>, period: Int): List<Double?> = ewmNullable(values.map { it }, period)

    private fun ewmNullable(
        values: List<Double?>,
        period: Int,
        smoothingAlpha: Double = 2.0 / (period + 1.0),
    ): List<Double?> {
        require(period > 0)
        require(smoothingAlpha > 0.0 && smoothingAlpha <= 1.0)
        val alpha = smoothingAlpha
        var current: Double? = null
        var observed = 0
        return values.map { value ->
            if (value == null || !value.isFinite()) return@map null
            current = if (current == null) value else alpha * value + (1.0 - alpha) * current!!
            observed++
            if (observed >= period) current else null
        }
    }

    /** Wilder's smoothing: seeds with simple average of first `period` values, then smooths. Null values treated as 0. */
    private fun wilderSmooth(values: List<Double?>, period: Int): List<Double?> {
        require(period > 0)
        var current: Double? = null
        var sum = 0.0
        var count = 0
        return values.map { value ->
            val v = if (value == null || !value.isFinite()) 0.0 else value
            sum += v
            count++
            if (count < period) {
                current = null
                return@map null
            }
            if (current == null) {
                current = sum / period
            } else {
                current = (current * (period - 1) + v) / period
            }
            current
        }
    }

    private fun rollingMean(values: List<Double>, period: Int): List<Double?> =
        rollingMeanNullable(values.map { it }, period)

    private fun rollingMeanNullable(values: List<Double?>, period: Int): List<Double?> = values.indices.map { i ->
        if (i + 1 < period) null
        else {
            val window = values.subList(i - period + 1, i + 1)
            if (window.any { it == null || !it.isFinite() }) null else window.sumOf { it!! } / period
        }
    }

    private fun rollingPopulationStd(values: List<Double>, period: Int): List<Double?> = values.indices.map { i ->
        if (i + 1 < period) null
        else {
            val window = values.subList(i - period + 1, i + 1)
            val mean = window.average()
            sqrt(window.sumOf { (it - mean) * (it - mean) } / period)
        }
    }
}
