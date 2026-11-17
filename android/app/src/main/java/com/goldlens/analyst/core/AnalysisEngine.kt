package com.goldlens.analyst.core

import kotlin.math.abs

/** Deterministic, auditable signal engine. Scores are evidence points, not win probabilities. */
object AnalysisEngine {
    fun analyze(
        dataset: MarketDataset,
        nowMillis: Long = System.currentTimeMillis(),
        maxLiveAgeMillis: Long = 30L * 60L * 1000L,
    ): AnalysisResult {
        val candles = try {
            normalizeCandles(dataset.candles)
        } catch (error: IllegalArgumentException) {
            return wait(dataset, null, null, "No valid candle data: ${error.message}")
        }
        val last = candles.last()
        val allIndicators = TechnicalIndicators.calculate(candles)
        val latest = allIndicators.lastOrNull()
        val structure = MarketStructureAnalyzer.analyze(candles)
        val zones = SupportResistanceDetector.detect(candles, dataset.timeframe, allIndicators)
        if (latest == null) return wait(dataset, last.close, last.timestampMillis, "Indicator calculation failed.", zones, structure)

        val liveSource = dataset.source == DataSourceKind.LIVE || dataset.source == DataSourceKind.DELAYED
        if (liveSource && (last.timestampMillis > nowMillis + 60_000 || nowMillis - last.timestampMillis > maxLiveAgeMillis)) {
            return wait(dataset, last.close, last.timestampMillis, "Market candle is stale or timestamped in the future; no directional signal issued.", zones, structure, latest)
        }
        val expectedInterval = timeframeMillis(dataset.timeframe)
        if (expectedInterval != null && candles.size >= 3) {
            val recent = candles.takeLast(4)
            val hasRecentGap = recent.zipWithNext().any { (a, b) -> b.timestampMillis - a.timestampMillis > expectedInterval * 2 }
            if (hasRecentGap) return wait(dataset, last.close, last.timestampMillis, "Recent candle gaps make the market sequence incomplete; no directional signal issued.", zones, structure, latest)
        }
        if (candles.size < 200 || latest.ema200 == null || latest.atr14 == null) {
            return wait(dataset, last.close, last.timestampMillis, "At least 200 valid candles are required for the full EMA trend assessment.", zones, structure, latest)
        }

        var score = 0
        val evidence = mutableListOf<String>()
        val conflicts = mutableListOf<String>()
        val price = last.close
        val ema20 = latest.ema20!!
        val ema50 = latest.ema50!!
        val ema100 = latest.ema100!!
        val ema200 = latest.ema200!!
        val emaAlignedUp = ema20 > ema50 && ema50 > ema100 && ema100 > ema200 && price > ema20
        val emaAlignedDown = ema20 < ema50 && ema50 < ema100 && ema100 < ema200 && price < ema20
        when {
            emaAlignedUp -> { score++; evidence += "Price and EMA 20/50/100/200 are aligned upward." }
            emaAlignedDown -> { score--; evidence += "Price and EMA 20/50/100/200 are aligned downward." }
            else -> conflicts += "EMA trend alignment is mixed."
        }

        val rsi = latest.rsi14
        if (rsi != null) when {
            rsi in 52.0..70.0 -> { score++; evidence += "RSI(14) supports bullish momentum (${format(rsi)})." }
            rsi in 30.0..48.0 -> { score--; evidence += "RSI(14) supports bearish momentum (${format(rsi)})." }
            rsi > 70.0 -> conflicts += "RSI(14) is overbought (${format(rsi)}); avoid chasing."
            rsi < 30.0 -> conflicts += "RSI(14) is oversold (${format(rsi)}); avoid chasing."
            else -> conflicts += "RSI(14) is neutral (${format(rsi)})."
        } else conflicts += "RSI is unavailable during warm-up."

        val histogram = latest.macdHistogram
        if (histogram != null) when {
            histogram > 0 -> { score++; evidence += "MACD histogram is positive." }
            histogram < 0 -> { score--; evidence += "MACD histogram is negative." }
        } else conflicts += "MACD is unavailable during warm-up."

        when (structure.trend) {
            "bullish structure" -> { score++; evidence += "Confirmed swings show higher highs and higher lows." }
            "bearish structure" -> { score--; evidence += "Confirmed swings show lower highs and lower lows." }
            else -> conflicts += "Swing structure is mixed or insufficient."
        }

        val atr = latest.atr14
        val support = zones.filter { it.side == ZoneSide.SUPPORT && it.status == ZoneStatus.ACTIVE }
            .minByOrNull { abs(price - it.midpoint) }
        val resistance = zones.filter { it.side == ZoneSide.RESISTANCE && it.status == ZoneStatus.ACTIVE }
            .minByOrNull { abs(price - it.midpoint) }
        if (support != null && abs(price - support.midpoint) <= atr * 0.6) {
            score++; evidence += "Price is near ${support.strength} ${support.timeframe} support (${format(support.lower)}–${format(support.upper)})."
        }
        if (resistance != null && abs(price - resistance.midpoint) <= atr * 0.6) {
            score--; conflicts += "Price is near ${resistance.strength} ${resistance.timeframe} resistance (${format(resistance.lower)}–${format(resistance.upper)})."
        }
        val retestZone = zones.firstOrNull { it.status == ZoneStatus.RETESTING }
        if (retestZone != null) {
            when (retestZone.side) {
                ZoneSide.RESISTANCE -> { score++; evidence += "Broken resistance is being retested from above." }
                ZoneSide.SUPPORT -> { score--; evidence += "Broken support is being retested from below." }
            }
        }

        val direction = when {
            score >= 3 -> Direction.BUY
            score <= -3 -> Direction.SELL
            else -> Direction.WAIT
        }
        val entry = if (direction == Direction.WAIT) null else price
        val stop = when (direction) {
            Direction.BUY -> price - 1.5 * atr
            Direction.SELL -> price + 1.5 * atr
            Direction.WAIT -> null
        }
        val target = when (direction) {
            Direction.BUY -> price + 3.0 * atr
            Direction.SELL -> price - 3.0 * atr
            Direction.WAIT -> null
        }
        val reason = when (direction) {
            Direction.BUY -> "Bullish conditions meet the evidence threshold. This is a technical assessment, not a probability or trade instruction."
            Direction.SELL -> "Bearish conditions meet the evidence threshold. This is a technical assessment, not a probability or trade instruction."
            Direction.WAIT -> "Evidence is mixed or insufficient; wait for better confirmation. No trade levels are issued."
        }
        return AnalysisResult(
            direction, price, last.timestampMillis, dataset.timeframe, dataset.source, score,
            reason, evidence, conflicts, latest, structure, zones, entry, stop, target,
            if (direction == Direction.WAIT) null else 2.0,
        )
    }

    private fun wait(
        dataset: MarketDataset,
        price: Double?,
        timestamp: Long?,
        reason: String,
        zones: List<PriceZone> = emptyList(),
        structure: SwingStructure? = null,
        indicators: IndicatorValues? = null,
    ) = AnalysisResult(Direction.WAIT, price, timestamp, dataset.timeframe, dataset.source,
        0, reason, emptyList(), listOf(reason), indicators, structure, zones, null, null, null, null)

    private fun timeframeMillis(timeframe: String): Long? = when (timeframe.lowercase()) {
        "1m", "1min" -> 60_000L
        "5m", "5min" -> 5 * 60_000L
        "15m", "15min" -> 15 * 60_000L
        "30m", "30min" -> 30 * 60_000L
        "1h", "60m" -> 60 * 60_000L
        "4h" -> 4 * 60 * 60_000L
        "1d", "1day", "daily" -> 24 * 60 * 60_000L
        else -> null
    }

    private fun format(value: Double) = String.format(java.util.Locale.US, "%.2f", value)
}
