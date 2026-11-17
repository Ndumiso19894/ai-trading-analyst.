package com.goldlens.analyst.core

import kotlin.math.floor

private val FRAME_MILLIS = mapOf(
    "1m" to 60_000L, "5m" to 5 * 60_000L, "15m" to 15 * 60_000L,
    "30m" to 30 * 60_000L, "1h" to 60 * 60_000L, "4h" to 4 * 60 * 60_000L,
    "1d" to 24 * 60 * 60_000L,
)

/** Aggregates only complete, UTC-aligned buckets; missing source candles make a target bucket unavailable. */
fun aggregateCandles(candles: List<Candle>, sourceTimeframe: String, targetTimeframe: String): List<Candle> {
    val sourceMillis = FRAME_MILLIS[sourceTimeframe.lowercase()] ?: return emptyList()
    val targetMillis = FRAME_MILLIS[targetTimeframe.lowercase()] ?: return emptyList()
    if (targetMillis < sourceMillis || targetMillis % sourceMillis != 0L) return emptyList()
    val normalized = runCatching { normalizeCandles(candles) }.getOrElse { return emptyList() }
    if (targetMillis == sourceMillis) return normalized
    val expectedCount = (targetMillis / sourceMillis).toInt()
    return normalized.groupBy { floor(it.timestampMillis.toDouble() / targetMillis).toLong() * targetMillis }
        .toSortedMap()
        .mapNotNull { (bucketStart, bucket) ->
            val ordered = bucket.sortedBy(Candle::timestampMillis)
            if (ordered.size != expectedCount) return@mapNotNull null
            if (ordered.withIndex().any { (index, candle) -> candle.timestampMillis != bucketStart + index * sourceMillis }) return@mapNotNull null
            Candle(
                timestampMillis = bucketStart,
                open = ordered.first().open,
                high = ordered.maxOf(Candle::high),
                low = ordered.minOf(Candle::low),
                close = ordered.last().close,
                volume = ordered.sumOf(Candle::volume),
            ).takeIf(Candle::isValid)
        }
}

fun timeframeDurationMillis(timeframe: String): Long? = FRAME_MILLIS[timeframe.lowercase()]

/** Removes an in-progress candle by treating each timestamp as its UTC opening time. */
fun completeCandlesAt(candles: List<Candle>, timeframe: String, nowMillis: Long): List<Candle> {
    val duration = timeframeDurationMillis(timeframe) ?: return emptyList()
    if (nowMillis <= 0L) return emptyList()
    return candles.filter { it.timestampMillis <= nowMillis - duration }
}
