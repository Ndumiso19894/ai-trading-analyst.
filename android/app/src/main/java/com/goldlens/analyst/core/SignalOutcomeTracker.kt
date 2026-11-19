package com.goldlens.analyst.core

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks signal outcomes for accuracy measurement. Stored in memory during the app session;
 * can be persisted to Room in the future.
 */
object SignalOutcomeTracker {

    data class SignalRecord(
        val symbol: String,
        val timeframe: String,
        val direction: Direction,
        val entry: Double?,
        val stopLoss: Double?,
        val takeProfit: Double?,
        val score: Int,
        val rewardRisk: Double?,
        val openedAtMillis: Long,
        var outcome: Outcome? = null,
        var closedAtMillis: Long? = null,
        var exitPrice: Double? = null,
    )

    enum class Outcome { WIN, LOSS, BREAKEVEN, EXPIRED }

    private val records = ConcurrentHashMap<String, MutableList<SignalRecord>>()

    fun recordSignal(symbol: String, timeframe: String, direction: Direction,
                     entry: Double?, stopLoss: Double?, takeProfit: Double?,
                     score: Int, rewardRisk: Double?): String {
        val id = "${symbol}_${timeframe}_${System.currentTimeMillis()}"
        val record = SignalRecord(symbol, timeframe, direction, entry, stopLoss, takeProfit,
            score, rewardRisk, System.currentTimeMillis())
        records.computeIfAbsent(symbol) { mutableListOf() }.add(record)
        return id
    }

    fun updateOutcome(symbol: String, signalId: String, outcome: Outcome, exitPrice: Double?) {
        val symbolRecords = records[symbol] ?: return
        val record = symbolRecords.find { "${it.symbol}_${it.timeframe}_${it.openedAtMillis}" == signalId }
        if (record != null) {
            record.outcome = outcome
            record.closedAtMillis = System.currentTimeMillis()
            record.exitPrice = exitPrice
        }
    }

    /** Mark signals older than maxAgeMillis as EXPIRED if still unresolved. Call periodically. */
    fun expireStaleSignals(maxAgeMillis: Long = 24L * 60L * 60L * 1000L, nowMillis: Long = System.currentTimeMillis()): Int {
        var expired = 0
        records.values.forEach { symbolRecords ->
            symbolRecords.filter { it.outcome == null && (nowMillis - it.openedAtMillis) > maxAgeMillis }.forEach { record ->
                record.outcome = Outcome.EXPIRED
                record.closedAtMillis = nowMillis
                expired++
            }
        }
        return expired
    }

    fun winRate(symbol: String? = null): Double? {
        val all = if (symbol != null) records[symbol] ?: return null else records.values.flatten()
        val closed = all.filter { it.outcome != null }
        if (closed.isEmpty()) return null
        val wins = closed.count { it.outcome == Outcome.WIN }
        return wins * 100.0 / closed.size
    }

    fun totalSignals(symbol: String? = null): Int {
        return if (symbol != null) records[symbol]?.size ?: 0 else records.values.sumOf { it.size }
    }

    fun recentRecords(limit: Int = 50): List<SignalRecord> {
        return records.values.flatten()
            .sortedByDescending { it.openedAtMillis }
            .take(limit)
    }

    fun statsBySymbol(): Map<String, Pair<Int, Double?>> {
        return records.entries.associate { entry ->
            val symbol = entry.key
            val symbolRecords = entry.value
            val closed = symbolRecords.filter { it.outcome != null }
            val winRate = if (closed.isEmpty()) null else closed.count { it.outcome == Outcome.WIN } * 100.0 / closed.size
            symbol to Pair(symbolRecords.size, winRate)
        }
    }
}
