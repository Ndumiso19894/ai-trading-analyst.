package com.goldlens.analyst.data.market

import com.goldlens.analyst.core.Candle
import com.goldlens.analyst.core.aggregateCandles
import com.goldlens.analyst.core.completeCandlesAt
import com.goldlens.analyst.core.DataSourceKind
import com.goldlens.analyst.core.InstrumentPrice
import com.goldlens.analyst.core.normalizeCandles
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Replaceable provider boundary; implementations must state provenance and must not synthesize quotes. */
interface MarketDataProvider {
    val id: String
    val displayName: String
    fun fetch(instrument: String, timeframe: String): ProviderMarketSnapshot
}

data class ProviderMarketSnapshot(
    val instrument: String,
    val timeframe: String,
    val candles: List<Candle>,
    val source: DataSourceKind,
    val sourceLabel: String,
    val fetchedAtMillis: Long,
    val sourcePrecision: Int? = null,
    val providerReferencePrice: Double?,
    val providerPriceTimestampMillis: Long?,
    val bid: Double? = null,
    val ask: Double? = null,
    val note: String,
)

class MarketDataException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Keyless access to Yahoo's unofficial chart endpoint, matching the repository's optional Python
 * adapter. It is not an exchange feed: delivery may be delayed, changed, or rate limited. The
 * caller must display this caveat and treat absent symbols/fields as unavailable.
 */
class YahooFinanceChartProvider(
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : MarketDataProvider {
    override val id = "yahoo-chart-unofficial"
    override val displayName = "Yahoo Finance chart (unofficial; delay not guaranteed)"

    override fun fetch(instrument: String, timeframe: String): ProviderMarketSnapshot {
        val (interval, range) = requestParameters(timeframe)
        val ticker = instrument.replace("/", "") + "=X"
        val encodedTicker = URLEncoder.encode(ticker, Charsets.UTF_8.name())
        val url = URL("https://query1.finance.yahoo.com/v8/finance/chart/$encodedTicker?interval=$interval&range=$range&includePrePost=false")
        val payload = requestJson(url)
        val chart = payload.optJSONObject("chart") ?: throw MarketDataException("Provider response had no chart object.")
        val providerError = chart.optJSONObject("error")
        if (providerError != null && !providerError.isNull("description")) {
            throw MarketDataException("Provider rejected $instrument: ${providerError.optString("description")}")
        }
        val result = chart.optJSONArray("result")?.optJSONObject(0)
            ?: throw MarketDataException("No provider candles are available for $instrument at $timeframe.")
        val timestamps = result.optJSONArray("timestamp")
            ?: throw MarketDataException("Provider returned no timestamps for $instrument.")
        val quote = result.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)
            ?: throw MarketDataException("Provider returned no OHLC quote data for $instrument.")
        val opens = quote.optJSONArray("open")
        val highs = quote.optJSONArray("high")
        val lows = quote.optJSONArray("low")
        val closes = quote.optJSONArray("close")
        val volumes = quote.optJSONArray("volume")
        val candles = buildList {
            for (index in 0 until timestamps.length()) {
                val seconds = timestamps.optLong(index, -1L)
                val open = opens?.finiteDouble(index)
                val high = highs?.finiteDouble(index)
                val low = lows?.finiteDouble(index)
                val close = closes?.finiteDouble(index)
                if (seconds <= 0 || open == null || high == null || low == null || close == null) continue
                val volume = volumes?.finiteDouble(index)?.coerceAtLeast(0.0) ?: 0.0
                val candle = Candle(seconds * 1_000L, open, high, low, close, volume)
                if (candle.isValid()) add(candle)
            }
        }
        if (candles.isEmpty()) throw MarketDataException("Provider returned no valid OHLC candles for $instrument.")
        val normalized = normalizeCandles(candles)
        val meta = result.optJSONObject("meta") ?: JSONObject()
        val metaPrecision = meta.optInt("priceHint", -1).takeIf { it in 0..8 }
        val observedPrecision = InstrumentPrice.inferPrecision(normalized.flatMap { listOf(it.open, it.high, it.low, it.close) })
        val sourcePrecision = listOfNotNull(metaPrecision, observedPrecision).maxOrNull()
        val fetchedAtMillis = clockMillis()
        val sourceTimeframe = if (timeframe == "4h") "1h" else timeframe
        val closedSourceCandles = completeCandlesAt(normalized, sourceTimeframe, fetchedAtMillis)
        val requestedCandles = if (timeframe == "4h") aggregateCandles(closedSourceCandles, "1h", "4h") else closedSourceCandles
        if (requestedCandles.isEmpty()) throw MarketDataException("Provider supplied no complete, closed $timeframe candles for $instrument.")
        val referencePrice = meta.finiteDouble("regularMarketPrice")
        val referenceTimestamp = meta.optLong("regularMarketTime", -1L).takeIf { it > 0 }?.times(1_000L)
        return ProviderMarketSnapshot(
            instrument = instrument,
            timeframe = timeframe,
            candles = requestedCandles,
            source = DataSourceKind.HISTORICAL,
            sourceLabel = "Historical chart archive · $displayName · ${meta.optString("symbol", ticker)}",
            fetchedAtMillis = fetchedAtMillis,
            sourcePrecision = sourcePrecision,
            providerReferencePrice = referencePrice,
            providerPriceTimestampMillis = referenceTimestamp,
            bid = meta.finiteDouble("bid"),
            ask = meta.finiteDouble("ask"),
            note = "Historical chart candles; live status and delay are not independently verifiable. Unofficial endpoint: availability, symbol coverage, bid/ask and licensing are not guaranteed. OHLC volume is provider-reported.",
        )
    }

    private fun requestJson(url: URL): JSONObject {
        val connection = try { connectionFactory(url) } catch (error: Exception) {
            throw MarketDataException("Could not connect to the market-data provider.", error)
        }
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "GoldLensAndroid/0.5")
            val status = connection.responseCode
            if (status == 429) throw MarketDataException("Provider rate limit reached. Wait before retrying.")
            if (status !in 200..299) throw MarketDataException("Provider request failed with HTTP $status.")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return try { JSONObject(body) } catch (error: JSONException) {
                throw MarketDataException("Provider returned invalid JSON.", error)
            }
        } catch (error: MarketDataException) {
            throw error
        } catch (error: Exception) {
            throw MarketDataException("Market-data request failed: ${error.message ?: "network error"}", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun requestParameters(timeframe: String): Pair<String, String> = when (timeframe.lowercase()) {
        "1m" -> "1m" to "5d"
        "5m" -> "5m" to "1mo"
        "15m" -> "15m" to "1mo"
        "30m" -> "30m" to "1mo"
        "1h" -> "60m" to "3mo"
        "4h" -> "60m" to "6mo"
        "1d" -> "1d" to "1y"
        else -> throw MarketDataException("Unsupported provider timeframe '$timeframe'.")
    }

    private fun org.json.JSONArray.finiteDouble(index: Int): Double? {
        if (index >= length() || isNull(index)) return null
        return optDouble(index, Double.NaN).takeIf(Double::isFinite)
    }

    private fun JSONObject.finiteDouble(name: String): Double? =
        if (isNull(name)) null else optDouble(name, Double.NaN).takeIf(Double::isFinite)

}
