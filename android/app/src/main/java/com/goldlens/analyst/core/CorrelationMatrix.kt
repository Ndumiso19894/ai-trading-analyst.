package com.goldlens.analyst.core

/**
 * Correlation groups for risk management. Instruments in the same group move together;
 * only one signal per group should be acted on at a time to avoid over-exposure.
 */
object CorrelationMatrix {

    private val groups = listOf(
        listOf("EUR/USD", "GBP/USD", "AUD/USD", "NZD/USD", "USD/CAD", "USD/CHF", "USD/ZAR"),
        listOf("USD/JPY", "EUR/JPY", "GBP/JPY"),
        listOf("EUR/GBP"),
        listOf("XAU/USD", "XAG/USD", "XPT/USD"),
    )

    /** Returns the group index for an instrument, or null if not found. */
    fun groupIndex(symbol: String): Int? = groups.indexOfFirst { it.contains(symbol) }

    /** Returns all symbols in the same group as the given instrument. */
    fun correlatedSymbols(symbol: String): List<String> = groups.firstOrNull { it.contains(symbol) } ?: emptyList()

    /** Whether two instruments are in the same correlation group. */
    fun areCorrelated(symbol1: String, symbol2: String): Boolean {
        if (symbol1 == symbol2) return true
        return groups.any { it.contains(symbol1) && it.contains(symbol2) }
    }
}
