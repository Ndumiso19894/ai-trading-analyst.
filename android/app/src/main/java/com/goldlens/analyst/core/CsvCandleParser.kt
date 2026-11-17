package com.goldlens.analyst.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class CsvImportResult(
    val candles: List<Candle>,
    val acceptedRows: Int,
    val skippedRows: Int,
    val sourcePrecision: Int? = null,
)

object CsvCandleParser {
    /** Exports normalized UTC OHLCV records in a round-trippable, locale-independent CSV format. */
    fun export(candles: List<Candle>): String = buildString {
        appendLine("timestamp,open,high,low,close,volume")
        normalizeCandles(candles).forEach { candle ->
            append(Instant.ofEpochMilli(candle.timestampMillis).toString())
            append(',').append(candle.open.toString())
            append(',').append(candle.high.toString())
            append(',').append(candle.low.toString())
            append(',').append(candle.close.toString())
            append(',').append(candle.volume.toString())
            appendLine()
        }
    }

    fun parse(csv: String): CsvImportResult {
        require(csv.length <= 20_000_000) { "CSV file exceeds the 20 MB import limit." }
        val records = parseRecords(csv)
        require(records.size >= 2) { "CSV needs a header and at least one candle row." }
        val headers = records.first().mapIndexed { index, text ->
            text.trim().removePrefix("\uFEFF").lowercase() to index
        }.toMap()
        val timeIndex = listOf("timestamp", "time", "datetime", "date").firstNotNullOfOrNull(headers::get)
            ?: throw IllegalArgumentException("CSV needs a timestamp, time, datetime or date column.")
        val openIndex = headers["open"] ?: throw IllegalArgumentException("CSV is missing the open column.")
        val highIndex = headers["high"] ?: throw IllegalArgumentException("CSV is missing the high column.")
        val lowIndex = headers["low"] ?: throw IllegalArgumentException("CSV is missing the low column.")
        val closeIndex = headers["close"] ?: throw IllegalArgumentException("CSV is missing the close column.")
        val volumeIndex = headers["volume"]
        val parsed = mutableListOf<Candle>()
        var skipped = 0
        var sourcePrecision: Int? = null
        records.drop(1).filter { row -> row.any(String::isNotBlank) }.forEach { row ->
            try {
                val rawPrices = listOf(row.required(openIndex), row.required(highIndex), row.required(lowIndex), row.required(closeIndex))
                val candle = Candle(
                    timestampMillis = parseTimestamp(row.required(timeIndex)),
                    open = rawPrices[0].toDouble(),
                    high = rawPrices[1].toDouble(),
                    low = rawPrices[2].toDouble(),
                    close = rawPrices[3].toDouble(),
                    volume = volumeIndex?.let { row.getOrNull(it)?.trim()?.takeIf(String::isNotEmpty)?.toDouble() } ?: 0.0,
                )
                if (candle.isValid()) {
                    parsed += candle
                    val rowPrecision = rawPrices.maxOf { decimalPlaces(it) }
                    sourcePrecision = maxOf(sourcePrecision ?: 0, rowPrecision)
                } else skipped++
            } catch (_: Exception) {
                skipped++
            }
        }
        require(parsed.isNotEmpty()) { "No valid OHLC rows could be read from the CSV." }
        val normalized = normalizeCandles(parsed)
        return CsvImportResult(normalized, normalized.size, skipped + (parsed.size - normalized.size), sourcePrecision)
    }

    private fun decimalPlaces(value: String): Int {
        val normalized = value.trim()
        val mantissa = normalized.substringBefore('e', missingDelimiterValue = normalized).substringBefore('E')
        val exponent = normalized.substringAfterLast('e', normalized.substringAfterLast('E', "0")).toIntOrNull() ?: 0
        return (mantissa.substringAfter('.', "").length - exponent).coerceIn(0, 8)
    }

    private fun List<String>.required(index: Int): String = getOrNull(index)?.trim()?.takeIf(String::isNotEmpty)
        ?: throw IllegalArgumentException("Missing value in column $index.")

    private fun parseTimestamp(text: String): Long {
        val numeric = text.toDoubleOrNull()
        if (numeric != null && numeric.isFinite()) {
            return if (numeric >= 1.0e12) numeric.toLong() else (numeric * 1000.0).toLong()
        }
        val value = text.trim()
        try { return Instant.parse(value).toEpochMilli() } catch (_: DateTimeParseException) { }
        try { return OffsetDateTime.parse(value).toInstant().toEpochMilli() } catch (_: DateTimeParseException) { }
        val localFormatters = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
        )
        localFormatters.forEach { formatter ->
            try { return LocalDateTime.parse(value, formatter).toInstant(ZoneOffset.UTC).toEpochMilli() }
            catch (_: DateTimeParseException) { }
        }
        try { return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        catch (_: DateTimeParseException) { }
        throw IllegalArgumentException("Unsupported timestamp '$value'. Use ISO-8601 UTC or epoch seconds.")
    }

    /** Basic RFC-4180 field quoting, escaped quotes and embedded newlines. */
    private fun parseRecords(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += field.toString(); field.setLength(0) }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += field.toString(); field.setLength(0)
                    rows += row; row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        require(!quoted) { "CSV has an unclosed quoted field." }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }
}
