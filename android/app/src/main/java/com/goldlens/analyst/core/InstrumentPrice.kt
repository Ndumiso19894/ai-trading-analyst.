package com.goldlens.analyst.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Formatting and distance helpers. They never modify stored prices or analytical calculations. */
object InstrumentPrice {
    fun precision(symbol: String, sourcePrecision: Int? = null): Int {
        require(sourcePrecision == null || sourcePrecision in 0..8) { "Source precision must be between 0 and 8 decimal places." }
        return sourcePrecision ?: InstrumentRegistry.require(symbol).displayDecimals
    }

    fun format(value: Double, symbol: String, sourcePrecision: Int? = null): String {
        if (!value.isFinite()) return "—"
        val decimals = precision(symbol, sourcePrecision)
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toPlainString()
    }

    /** Best visible scale from numeric source values; callers may combine it with provider metadata. */
    fun inferPrecision(values: Iterable<Double>): Int? = values
        .filter(Double::isFinite)
        .map { BigDecimal.valueOf(it).stripTrailingZeros().scale().coerceIn(0, 8) }
        .maxOrNull()

    /** A Forex pip distance. Returns null for metals and unsupported instruments. */
    fun pipDistance(symbol: String, from: Double, to: Double): Double? {
        val spec = InstrumentRegistry.find(symbol) ?: return null
        val pip = spec.pipSize ?: return null
        if (!from.isFinite() || !to.isFinite()) return null
        return kotlin.math.abs(to - from) / pip
    }

    /** A metal point distance, only when an instrument-specific point size is configured. */
    fun pointDistance(symbol: String, from: Double, to: Double, configuredPointSize: Double? = null): Double? {
        val spec = InstrumentRegistry.find(symbol) ?: return null
        if (spec.assetClass != AssetClass.PRECIOUS_METAL || !from.isFinite() || !to.isFinite()) return null
        val point = configuredPointSize ?: spec.pointSize ?: return null
        if (!point.isFinite() || point <= 0.0) return null
        return kotlin.math.abs(to - from) / point
    }

    /** Signed candle-to-candle movement with a market-appropriate unit label. */
    fun movement(
        symbol: String,
        from: Double,
        to: Double,
        sourcePrecision: Int? = null,
        configuredPointSize: Double? = null,
    ): String {
        if (!from.isFinite() || !to.isFinite()) return "Unavailable"
        val delta = to - from
        val signed = if (delta > 0.0) "+" else if (delta < 0.0) "−" else ""
        val amount = format(kotlin.math.abs(delta), symbol, sourcePrecision)
        val pip = pipDistance(symbol, from, to)
        if (pip != null) return "$signed$amount (${unitNumber(pip)} pips)"
        val points = pointDistance(symbol, from, to, configuredPointSize)
        if (points != null) return "$signed$amount (${unitNumber(points)} points)"
        val quoteCurrency = InstrumentRegistry.find(symbol)?.quoteCurrency ?: "price"
        return "$signed$amount $quoteCurrency price units"
    }

    fun unitDistanceText(symbol: String, from: Double?, to: Double?, configuredPointSize: Double? = null): String? {
        if (from == null || to == null || !from.isFinite() || !to.isFinite()) return null
        val pips = pipDistance(symbol, from, to)
        if (pips != null) return "${unitNumber(pips)} pips"
        val points = pointDistance(symbol, from, to, configuredPointSize)
        if (points != null) return "${unitNumber(points)} points"
        return null
    }

    private fun unitNumber(value: Double): String = String.format(Locale.US, "%.2f", value)
        .trimEnd('0').trimEnd('.')
}
