package com.goldlens.analyst.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Confirmed-pivot S/R zones. Only candles in the supplied prefix are considered. */
object SupportResistanceDetector {
    private data class Pivot(val index: Int, val price: Double, val rejection: Double)
    private data class Cluster(val side: ZoneSide, val pivots: MutableList<Pivot>, var center: Double, var width: Double)

    fun detect(
        candles: List<Candle>,
        timeframe: String,
        indicators: List<IndicatorValues> = TechnicalIndicators.calculate(candles),
        leftBars: Int = 2,
        rightBars: Int = 2,
        minTouchSeparationBars: Int = 4,
        maxZonesPerSide: Int = 5,
    ): List<PriceZone> {
        require(leftBars >= 1 && rightBars >= 1)
        if (candles.size < leftBars + rightBars + 1) return emptyList()
        val pivots = mutableListOf<Pair<ZoneSide, Pivot>>()
        // A pivot is emitted only after its right-hand confirmation bars are present.
        for (i in leftBars until candles.size - rightBars) {
            val bar = candles[i]
            val left = candles.subList(i - leftBars, i)
            val right = candles.subList(i + 1, i + rightBars + 1)
            val range = max(bar.high - bar.low, bar.close * 0.0001).coerceAtLeast(1e-9)
            val wickHigh = (bar.high - max(bar.open, bar.close)).coerceAtLeast(0.0)
            val wickLow = (min(bar.open, bar.close) - bar.low).coerceAtLeast(0.0)
            val rejectionHigh = (wickHigh / range).coerceIn(0.0, 1.0)
            val rejectionLow = (wickLow / range).coerceIn(0.0, 1.0)
            val highPivot = bar.high >= left.maxOf(Candle::high) && bar.high >= right.maxOf(Candle::high) &&
                (bar.high > left.maxOf(Candle::high) || bar.high > right.maxOf(Candle::high))
            val lowPivot = bar.low <= left.minOf(Candle::low) && bar.low <= right.minOf(Candle::low) &&
                (bar.low < left.minOf(Candle::low) || bar.low < right.minOf(Candle::low))
            if (highPivot) pivots += ZoneSide.RESISTANCE to Pivot(i, bar.high, rejectionHigh)
            if (lowPivot) pivots += ZoneSide.SUPPORT to Pivot(i, bar.low, rejectionLow)
        }
        val clusters = mutableListOf<Cluster>()
        for ((side, pivot) in pivots.sortedBy { it.second.index }) {
            val atr = indicators.getOrNull(pivot.index)?.atr14 ?: candles[pivot.index].close * 0.002
            val halfWidth = max(atr * 0.15, pivot.price * 0.00025)
            val tolerance = max(atr * 0.35, pivot.price * 0.0005)
            val existing = clusters.firstOrNull {
                it.side == side && abs(it.center - pivot.price) <= max(it.width + halfWidth, tolerance)
            }
            if (existing == null) {
                clusters += Cluster(side, mutableListOf(pivot), pivot.price, halfWidth)
            } else {
                val oldCount = existing.pivots.size
                existing.center = (existing.center * oldCount + pivot.price) / (oldCount + 1)
                existing.width = max(existing.width, halfWidth)
                existing.pivots += pivot
            }
        }

        val lastIndex = candles.lastIndex
        return clusters.mapNotNull { cluster ->
            val ordered = cluster.pivots.sortedBy { it.index }
            val separated = mutableListOf<Pivot>()
            ordered.forEach { pivot ->
                if (separated.isEmpty() || pivot.index - separated.last().index >= minTouchSeparationBars) separated += pivot
            }
            if (separated.isEmpty()) return@mapNotNull null
            val half = cluster.width
            val lower = cluster.center - half
            val upper = cluster.center + half
            val lastTouch = separated.last()
            val rejection = separated.map(Pivot::rejection).average().coerceIn(0.0, 1.0)
            val barsSinceTouch = (lastIndex - lastTouch.index).coerceAtLeast(0)
            val touchesScore = (separated.size * 0.15).coerceAtMost(0.60)
            val recencyScore = 0.15 * exp(-barsSinceTouch.toDouble() / max(20, candles.size / 3))
            val score = touchesScore + rejection * 0.25 + recencyScore
            val strength = when {
                score >= 0.65 -> "strong"
                score >= 0.38 -> "moderate"
                else -> "weak"
            }
            val breakIndex = (lastTouch.index + 1..lastIndex).firstOrNull { index ->
                val c = candles[index]
                if (cluster.side == ZoneSide.SUPPORT) c.low < lower else c.high > upper
            }
            val current = candles.last()
            val retesting = breakIndex != null && when (cluster.side) {
                ZoneSide.SUPPORT -> current.high >= lower && current.low <= upper && current.close < cluster.center
                ZoneSide.RESISTANCE -> current.low <= upper && current.high >= lower && current.close > cluster.center
            }
            val status = when {
                breakIndex == null -> ZoneStatus.ACTIVE
                retesting -> ZoneStatus.RETESTING
                else -> ZoneStatus.BROKEN
            }
            PriceZone(
                side = cluster.side, lower = lower, upper = upper, midpoint = cluster.center,
                touches = separated.size, rejectionScore = score, strength = strength,
                timeframe = timeframe, lastTouchMillis = candles[lastTouch.index].timestampMillis,
                status = status,
            )
        }.sortedWith(compareBy<PriceZone> { abs(it.midpoint - candles.last().close) }
            .thenByDescending { it.rejectionScore })
            .groupBy(PriceZone::side)
            .values.flatMap { zones -> zones.take(maxZonesPerSide) }
    }
}

object MarketStructureAnalyzer {
    fun analyze(candles: List<Candle>, leftBars: Int = 2, rightBars: Int = 2): SwingStructure {
        if (candles.size < leftBars + rightBars + 1) return SwingStructure("insufficient swing points", emptyList(), emptyList())
        val highs = mutableListOf<Double>()
        val lows = mutableListOf<Double>()
        for (i in leftBars until candles.size - rightBars) {
            val bar = candles[i]
            val left = candles.subList(i - leftBars, i)
            val right = candles.subList(i + 1, i + rightBars + 1)
            if (bar.high >= left.maxOf(Candle::high) && bar.high >= right.maxOf(Candle::high) &&
                (bar.high > left.maxOf(Candle::high) || bar.high > right.maxOf(Candle::high))) highs += bar.high
            if (bar.low <= left.minOf(Candle::low) && bar.low <= right.minOf(Candle::low) &&
                (bar.low < left.minOf(Candle::low) || bar.low < right.minOf(Candle::low))) lows += bar.low
        }
        val recentHighs = highs.takeLast(2)
        val recentLows = lows.takeLast(2)
        val trend = if (recentHighs.size < 2 || recentLows.size < 2) "insufficient swing points" else when {
            recentHighs[1] > recentHighs[0] && recentLows[1] > recentLows[0] -> "bullish structure"
            recentHighs[1] < recentHighs[0] && recentLows[1] < recentLows[0] -> "bearish structure"
            else -> "mixed structure"
        }
        return SwingStructure(trend, recentHighs, recentLows)
    }
}
