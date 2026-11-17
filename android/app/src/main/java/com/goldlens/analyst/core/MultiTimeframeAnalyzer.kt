package com.goldlens.analyst.core

/** A timeframe is unavailable when the source lacks complete aligned bars; it is never synthesized downward. */
data class TimeframeAssessment(
    val timeframe: String,
    val candleCount: Int,
    val result: AnalysisResult?,
    val status: String,
)

data class MultiTimeframeResult(
    val instrument: String,
    val sourceTimeframe: String,
    val assessments: List<TimeframeAssessment>,
    val agreement: String,
    val explanation: String,
)

object MultiTimeframeAnalyzer {
    val timeframes = listOf("5m", "15m", "30m", "1h", "4h", "1d")

    fun analyze(dataset: MarketDataset): MultiTimeframeResult {
        val assessments = timeframes.map { target ->
            val candles = when {
                target == dataset.timeframe -> runCatching { normalizeCandles(dataset.candles) }.getOrDefault(emptyList())
                else -> aggregateCandles(dataset.candles, dataset.timeframe, target)
            }
            val analysis = if (candles.isEmpty()) null else AnalysisEngine.analyze(
                dataset.copy(candles = candles, timeframe = target),
            )
            TimeframeAssessment(
                timeframe = target,
                candleCount = candles.size,
                result = analysis,
                status = when {
                    candles.isEmpty() -> "unavailable: source data cannot produce complete $target bars"
                    analysis?.direction == Direction.WAIT -> analysis.reason
                    else -> "available"
                },
            )
        }
        val directional = assessments.mapNotNull { it.result?.direction }.filter { it != Direction.WAIT }.toSet()
        val unavailable = assessments.count { it.result == null }
        val agreement = when {
            directional.size > 1 -> "conflicting"
            unavailable > 0 -> "partial"
            directional.isEmpty() -> "no-confirmation"
            else -> "aligned"
        }
        val explanation = when (agreement) {
            "conflicting" -> "Available timeframe signals disagree; wait for alignment."
            "partial" -> "$unavailable timeframe(s) lack aggregatable data; no confirmation is inferred for them."
            "aligned" -> "All available directional timeframe signals agree."
            else -> "No directional timeframe confirmation is available."
        }
        return MultiTimeframeResult(dataset.instrument, dataset.timeframe, assessments, agreement, explanation)
    }

    /** Conflicting available context blocks entries; unavailable frames are disclosed but do not erase a valid primary signal. */
    fun applyGate(primary: AnalysisResult, context: MultiTimeframeResult): AnalysisResult {
        if (primary.direction == Direction.WAIT) return primary
        val primaryDuration = timeframeDurationMillis(primary.timeframe) ?: return primary
        val higher = context.assessments.filter { (timeframeDurationMillis(it.timeframe) ?: 0L) > primaryDuration }
        if (higher.isEmpty()) return primary
        val missing = higher.filter { it.result == null || it.result.direction == Direction.WAIT }
        val opposing = higher.filter { it.result?.direction != null && it.result.direction != Direction.WAIT && it.result.direction != primary.direction }
        if (opposing.isNotEmpty()) {
            val reason = "Higher-timeframe direction conflicts: ${opposing.joinToString { "${it.timeframe} ${it.result?.direction}" }}."
            val missingNote = if (missing.isEmpty()) "" else " Additional higher-timeframe data is unavailable or neutral: ${missing.joinToString { it.timeframe }}."
            return primary.copy(
                direction = Direction.WAIT,
                reason = "WAIT: $reason$missingNote",
                conflicts = primary.conflicts + reason + missingNote,
                entry = null,
                stopLoss = null,
                takeProfit = null,
                rewardRisk = null,
            )
        }
        if (missing.isEmpty()) return primary
        val note = "Primary ${primary.timeframe} ${primary.direction} signal; higher-timeframe confirmation is incomplete for ${missing.joinToString { it.timeframe }}. Review those frames when data is available."
        return primary.copy(
            reason = "${primary.reason} $note",
            evidence = primary.evidence + note,
        )
    }
}
