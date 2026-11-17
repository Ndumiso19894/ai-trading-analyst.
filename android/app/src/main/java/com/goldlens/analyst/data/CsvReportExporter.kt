package com.goldlens.analyst.data

import java.time.Instant
import java.util.Locale

/** RFC-4180-style CSV exports for locally stored reports; no market data is fetched or inferred. */
object CsvReportExporter {
    fun analyses(rows: List<AnalysisEntity>): String = csv(
        listOf("timestamp_utc", "instrument", "timeframe", "source", "direction", "score", "price", "entry", "stop_loss", "take_profit", "reward_risk", "reason", "evidence", "conflicts"),
        rows.map { row -> listOf(
            instant(row.timestampMillis), row.symbol, row.timeframe, row.source, row.direction, row.score.toString(),
            number(row.price), number(row.entry), number(row.stopLoss), number(row.takeProfit), number(row.rewardRisk),
            row.reason, row.evidence, row.conflicts,
        ) },
    )

    fun zones(rows: List<Pair<AnalysisEntity, List<ZoneEntity>>>): String = csv(
        listOf("analysis_timestamp_utc", "instrument", "analysis_timeframe", "source", "direction", "zone_side", "lower", "upper", "midpoint", "touches", "strength", "zone_timeframe", "status", "last_touch_utc"),
        rows.flatMap { (analysis, zones) -> zones.map { zone -> listOf(
            instant(analysis.timestampMillis), analysis.symbol, analysis.timeframe, analysis.source, analysis.direction,
            zone.side, number(zone.lower), number(zone.upper), number(zone.midpoint), zone.touches.toString(),
            zone.strength, zone.timeframe, zone.status, instant(zone.lastTouchMillis),
        ) } },
    )

    fun journal(rows: List<JournalEntity>): String = csv(
        listOf("opened_at_utc", "closed_at_utc", "instrument", "direction", "timeframe", "entry", "stop_loss", "take_profit", "exit_price", "notes"),
        rows.map { row -> listOf(
            instant(row.openedAtMillis), row.closedAtMillis?.let(::instant).orEmpty(), row.symbol, row.direction,
            row.timeframe, number(row.entry), number(row.stopLoss), number(row.takeProfit), number(row.exitPrice), row.notes,
        ) },
    )

    fun backtests(rows: List<BacktestEntity>): String = csv(
        listOf("created_at_utc", "strategy", "timeframe", "source", "trades", "win_rate_percent", "profit_factor", "max_drawdown_per_unit", "net_result_per_unit", "report_json"),
        rows.map { row -> listOf(
            instant(row.createdAtMillis), row.strategy, row.timeframe, row.source, row.trades.toString(),
            number(row.winRate), number(row.profitFactor), number(row.maxDrawdown), number(row.netResult), row.reportJson,
        ) },
    )

    private fun csv(header: List<String>, rows: List<List<String>>): String = buildString {
        appendLine(header.joinToString(",", transform = ::escape))
        rows.forEach { row -> appendLine(row.joinToString(",", transform = ::escape)) }
    }

    private fun escape(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else value

    private fun instant(millis: Long): String = Instant.ofEpochMilli(millis).toString()
    private fun number(value: Double?): String = value?.let { String.format(Locale.US, "%.10g", it) }.orEmpty()
}
