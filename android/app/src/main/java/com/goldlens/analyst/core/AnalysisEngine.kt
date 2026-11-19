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

        val providerSource = dataset.source != DataSourceKind.IMPORTED_CSV
        val expectedInterval = timeframeMillis(dataset.timeframe)
        val freshnessLimit = maxLiveAgeMillis.coerceAtLeast((expectedInterval ?: 0L) * 2L)
        if (providerSource && (last.timestampMillis > nowMillis + 60_000 || nowMillis - last.timestampMillis > freshnessLimit)) {
            return wait(dataset, last.close, last.timestampMillis, "Market candle is stale or timestamped in the future; no directional signal issued.", zones, structure, latest)
        }
        if (expectedInterval != null && candles.size >= 3) {
            val recent = candles.takeLast(4)
            val hasRecentGap = recent.zipWithNext().any { (a, b) -> b.timestampMillis - a.timestampMillis > expectedInterval * 2 }
            if (hasRecentGap) return wait(dataset, last.close, last.timestampMillis, "Recent candle gaps make the market sequence incomplete; no directional signal issued.", zones, structure, latest)
        }
        // Timeframe-aware minimum: focus on recent candles, not ancient history.
        val minCandlesByTimeframe = mapOf(
            "5m" to 60, "15m" to 60, "30m" to 60,
            "1h" to 50, "4h" to 40, "1d" to 30,
        )
        val minCandles = minCandlesByTimeframe[dataset.timeframe.lowercase()] ?: 50
        if (candles.size < minCandles || latest.atr14 == null) {
            return wait(dataset, last.close, last.timestampMillis, "At least $minCandles valid candles are required for analysis.", zones, structure, latest)
        }

        var score = 0
        val evidence = mutableListOf<String>()
        val conflicts = mutableListOf<String>()
        val price = last.close
        val ema20 = latest.ema20!!
        val ema50 = latest.ema50!!
        val ema100 = latest.ema100!!
        // Use EMA200 only if available; don't force 200+ bars of ancient history.
        val ema200 = latest.ema200

        // Recent EMA alignment — check the last 5 candles for sustained alignment,
        // focusing on current trend rather than old history.
        val recentIndicators = allIndicators.takeLast(5)
        val recentBullish = recentIndicators.filter { it.ema20 != null && it.ema50 != null && it.ema100 != null }
            .count { it.ema20!! > it.ema50!! && it.ema50!! > it.ema100!! }
        val recentBearish = recentIndicators.filter { it.ema20 != null && it.ema50 != null && it.ema100 != null }
            .count { it.ema20!! < it.ema50!! && it.ema50!! < it.ema100!! }

        val emaTrendUp = recentBullish >= 3 && price > ema20 && (ema200 == null || price > ema200)
        val emaTrendDown = recentBearish >= 3 && price < ema20 && (ema200 == null || price < ema200)
        when {
            emaTrendUp -> { score += 2; evidence += "Recent EMA 20/50/100 aligned upward — current trend is bullish." }
            emaTrendDown -> { score -= 2; evidence += "Recent EMA 20/50/100 aligned downward — current trend is bearish." }
            else -> conflicts += "EMA trend is mixed — no clear directional bias."
        }

        val rsi = latest.rsi14
        if (rsi != null) when {
            rsi in 52.0..70.0 -> { score++; evidence += "RSI(14) supports bullish momentum (${formatMetric(rsi)})." }
            rsi in 30.0..48.0 -> { score--; evidence += "RSI(14) supports bearish momentum (${formatMetric(rsi)})." }
            rsi > 70.0 -> conflicts += "RSI(14) is overbought (${formatMetric(rsi)}); avoid chasing."
            rsi < 30.0 -> conflicts += "RSI(14) is oversold (${formatMetric(rsi)}); avoid chasing."
            else -> conflicts += "RSI(14) is neutral (${formatMetric(rsi)})."
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
            score++; evidence += "Price is near ${support.strength} ${support.timeframe} support (${InstrumentPrice.format(support.lower, dataset.instrument, dataset.sourcePrecision)}–${InstrumentPrice.format(support.upper, dataset.instrument, dataset.sourcePrecision)})."
        }
        if (resistance != null && abs(price - resistance.midpoint) <= atr * 0.6) {
            score--; conflicts += "Price is near ${resistance.strength} ${resistance.timeframe} resistance (${InstrumentPrice.format(resistance.lower, dataset.instrument, dataset.sourcePrecision)}–${InstrumentPrice.format(resistance.upper, dataset.instrument, dataset.sourcePrecision)})."
        }
        val retestZone = zones.firstOrNull { it.status == ZoneStatus.RETESTING }
        if (retestZone != null) {
            when (retestZone.side) {
                ZoneSide.RESISTANCE -> { score++; evidence += "Broken resistance is being retested from above." }
                ZoneSide.SUPPORT -> { score--; evidence += "Broken support is being retested from below." }
            }
        }

        // Volume confirmation — require above-average volume on the latest candle for signal strength
        val volumes = candles.map(Candle::volume)
        val avgVolume = if (volumes.size >= 20) volumes.dropLast(1).takeLast(20).average() else 0.0
        val latestVolume = volumes.lastOrNull() ?: 0.0
        val volumeConfirmed = avgVolume > 0 && latestVolume > avgVolume * 1.2
        if (!volumeConfirmed && avgVolume > 0) {
            conflicts += "Volume below recent average — signal needs confirmation."
        }

        // Candlestick pattern recognition
        val patterns = CandlestickPatternDetector.detect(candles)
        patterns.forEach { pattern ->
            when {
                pattern.bullish -> {
                    score += when (pattern.strength) { "strong" -> 2; "moderate" -> 1; "weak" -> 0; else -> 0 }
                    evidence += pattern.description
                }
                !pattern.bullish -> {
                    score -= when (pattern.strength) { "strong" -> 2; "moderate" -> 1; "weak" -> 0; else -> 0 }
                    conflicts += pattern.description
                }
            }
        }

        // Session filter — suppress signals during low-liquidity or closed sessions
        val sessionInfo = SessionFilter.currentSession(nowMillis)
        if (!sessionInfo.isActive) {
            score = 0
            conflicts += "Market session closed: ${sessionInfo.description}"
        } else if (!SessionFilter.isSessionFavorable(dataset.instrument, nowMillis)) {
            conflicts += "Not in favorable session for ${dataset.instrument}: ${sessionInfo.description}"
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
        val riskDistance = when (direction) {
            Direction.BUY -> kotlin.math.abs(price - stop!!)
            Direction.SELL -> kotlin.math.abs(stop!! - price)
            Direction.WAIT -> 0.0
        }
        val rewardDistance = when (direction) {
            Direction.BUY -> kotlin.math.abs(target!! - price)
            Direction.SELL -> kotlin.math.abs(price - target!!)
            Direction.WAIT -> 0.0
        }
        // Trailing stop: recent swing structure level, or 2x ATR beyond price if no swing data.
        val trailingStop = when (direction) {
            Direction.BUY -> {
                val recentSwingLow = structure?.swingLows?.lastOrNull()?.takeIf { it < price }
                (recentSwingLow ?: price - 2.0 * atr).coerceAtMost(price - 1.5 * atr)
            }
            Direction.SELL -> {
                val recentSwingHigh = structure?.swingHighs?.lastOrNull()?.takeIf { it > price }
                (recentSwingHigh ?: price + 2.0 * atr).coerceAtLeast(price + 1.5 * atr)
            }
            Direction.WAIT -> null
        }
        val calculatedRR = if (riskDistance > 0.0) rewardDistance / riskDistance else null
        val reason = when (direction) {
            Direction.BUY -> "Bullish conditions meet the evidence threshold. This is a technical assessment, not a probability or trade instruction."
            Direction.SELL -> "Bearish conditions meet the evidence threshold. This is a technical assessment, not a probability or trade instruction."
            Direction.WAIT -> "Evidence is mixed or insufficient; wait for better confirmation. No trade levels are issued."
        }
        return AnalysisResult(
            direction, price, last.timestampMillis, dataset.timeframe, dataset.source, score,
            reason, evidence, conflicts, latest, structure, zones, entry, stop, target,
            calculatedRR,
            instrument = dataset.instrument,
            trailingStop = trailingStop,
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
        0, reason, emptyList(), listOf(reason), indicators, structure, zones, null, null, null, null,
        instrument = dataset.instrument)

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

    private fun formatMetric(value: Double) = String.format(java.util.Locale.US, "%.2f", value)
}
