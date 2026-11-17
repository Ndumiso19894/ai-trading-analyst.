package com.goldlens.analyst.core

/** Instrument metadata template. Broker contract values stay null until explicitly configured. */
enum class AssetClass { FOREX, PRECIOUS_METAL }

data class InstrumentSpec(
    val symbol: String,
    val displayName: String,
    val assetClass: AssetClass,
    val brokerSymbol: String,
    val displayDecimals: Int,
    val quoteCurrency: String,
    val pipSize: Double? = null,
    /** Optional instrument-specific metal display point size; never inferred from Forex rules. */
    val pointSize: Double? = null,
    val tickSize: Double? = null,
    val tickValuePerLot: Double? = null,
    val contractSize: Double? = null,
    val minimumVolume: Double? = null,
    val volumeIncrement: Double? = null,
    val tradingSessionNote: String = "Session hours and contract details depend on the broker.",
) {
    init {
        require(symbol.matches(Regex("^[A-Z]{3}/[A-Z]{3}$")))
        require(displayDecimals in 0..8)
        require(pipSize == null || pipSize.isFinite() && pipSize > 0.0)
        require(pointSize == null || pointSize.isFinite() && pointSize > 0.0)
        require((tickSize == null) == (tickValuePerLot == null))
    }

    /** Safe for customized broker aliases, e.g. XAUUSD.a or EURUSDm. */
    fun withBrokerSymbol(alias: String): InstrumentSpec {
        require(alias.isNotBlank() && alias.length <= 32) { "Enter a broker symbol (up to 32 characters)." }
        return copy(brokerSymbol = alias.trim())
    }
}

object InstrumentRegistry {
    private fun forex(symbol: String, name: String, decimals: Int = 5) = InstrumentSpec(
        symbol = symbol,
        displayName = name,
        assetClass = AssetClass.FOREX,
        brokerSymbol = symbol.replace("/", ""),
        displayDecimals = decimals,
        quoteCurrency = symbol.substringAfter('/'),
        pipSize = if (symbol.endsWith("/JPY")) 0.01 else 0.0001,
    )

    private fun metal(symbol: String, name: String, decimals: Int = 2, pointSize: Double? = null) = InstrumentSpec(
        symbol = symbol,
        displayName = name,
        assetClass = AssetClass.PRECIOUS_METAL,
        brokerSymbol = symbol.replace("/", ""),
        displayDecimals = decimals,
        quoteCurrency = symbol.substringAfter('/'),
        pointSize = pointSize,
    )

    val instruments: List<InstrumentSpec> = listOf(
        forex("EUR/USD", "Euro / US Dollar"),
        forex("GBP/USD", "British Pound / US Dollar"),
        forex("USD/JPY", "US Dollar / Japanese Yen", 3),
        forex("USD/CHF", "US Dollar / Swiss Franc"),
        forex("AUD/USD", "Australian Dollar / US Dollar"),
        forex("NZD/USD", "New Zealand Dollar / US Dollar"),
        forex("USD/CAD", "US Dollar / Canadian Dollar"),
        forex("EUR/GBP", "Euro / British Pound"),
        forex("EUR/JPY", "Euro / Japanese Yen", 3),
        forex("GBP/JPY", "British Pound / Japanese Yen", 3),
        forex("USD/ZAR", "US Dollar / South African Rand"),
        metal("XAU/USD", "Gold / US Dollar"),
        metal("XAG/USD", "Silver / US Dollar", decimals = 3),
        metal("XPT/USD", "Platinum / US Dollar"),
    )

    private val bySymbol = instruments.associateBy(InstrumentSpec::symbol)

    fun find(symbol: String): InstrumentSpec? = bySymbol[symbol.uppercase()]

    fun require(symbol: String): InstrumentSpec = find(symbol)
        ?: throw IllegalArgumentException("Unsupported instrument '$symbol'. Add it to the instrument registry first.")
}
