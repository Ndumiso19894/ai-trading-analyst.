package com.goldlens.analyst.core

import kotlin.math.abs

/**
 * Candlestick pattern recognition for price-action confirmation.
 * All patterns are detected on completed candles only (no future data).
 */
object CandlestickPatternDetector {

    data class PatternResult(
        val name: String,
        val bullish: Boolean,
        val strength: String, // "weak", "moderate", "strong"
        val description: String,
    )

    /** Detect patterns on the last 3 candles. Returns all matching patterns. */
    fun detect(candles: List<Candle>): List<PatternResult> {
        if (candles.size < 3) return emptyList()
        val results = mutableListOf<PatternResult>()
        val c1 = candles[candles.size - 3] // oldest
        val c2 = candles[candles.size - 2] // middle
        val c3 = candles[candles.size - 1] // latest (most recent completed)

        results += detectEngulfing(c2, c3)
        results += detectHammer(c3)
        results += detectShootingStar(c3)
        results += detectDoji(c3)
        results += detectMorningStar(c1, c2, c3)
        results += detectEveningStar(c1, c2, c3)
        results += detectPiercingLine(c2, c3)
        results += detectDarkCloudCover(c2, c3)

        return results.filter { it != null } as List<PatternResult>
    }

    private fun detectEngulfing(c2: Candle, c3: Candle): PatternResult? {
        val c2Body = abs(c2.close - c2.open)
        val c3Body = abs(c3.close - c3.open)
        if (c3Body <= c2Body * 1.1) return null
        // Bullish engulfing: c2 bearish, c3 bullish, c3 opens below c2 close and closes above c2 open
        if (c2.close < c2.open && c3.close > c3.open &&
            c3.open < c2.close && c3.close > c2.open) {
            return PatternResult("Bullish Engulfing", true, "strong",
                "Bullish engulfing: buyers overtook sellers on the latest candle.")
        }
        // Bearish engulfing: c2 bullish, c3 bearish, c3 opens above c2 close and closes below c2 open
        if (c2.close > c2.open && c3.close < c3.open &&
            c3.open > c2.close && c3.close < c2.open) {
            return PatternResult("Bearish Engulfing", false, "strong",
                "Bearish engulfing: sellers overtook buyers on the latest candle.")
        }
        return null
    }

    private fun detectHammer(c: Candle): PatternResult? {
        val body = abs(c.close - c.open)
        val range = c.high - c.low
        if (range == 0.0) return null
        val lowerWick = minVal(c.open, c.close) - c.low
        val upperWick = c.high - maxVal(c.open, c.close)
        // Hammer: small body at top, long lower wick (2x body), little upper wick
        if (lowerWick > body * 2.0 && upperWick < body * 0.5 && body / range > 0.1) {
            return PatternResult("Hammer", true, "moderate",
                "Hammer: rejection of lower prices, potential bullish reversal.")
        }
        // Inverted hammer: small body at bottom, long upper wick
        if (upperWick > body * 2.0 && lowerWick < body * 0.5 && body / range > 0.1) {
            return PatternResult("Inverted Hammer", true, "moderate",
                "Inverted Hammer: rejection of higher prices, potential bullish reversal.")
        }
        return null
    }

    private fun detectShootingStar(c: Candle): PatternResult? {
        val body = abs(c.close - c.open)
        val range = c.high - c.low
        if (range == 0.0) return null
        val upperWick = c.high - maxVal(c.open, c.close)
        val lowerWick = minVal(c.open, c.close) - c.low
        if (upperWick > body * 2.0 && lowerWick < body * 0.5 && body / range > 0.1) {
            return PatternResult("Shooting Star", false, "moderate",
                "Shooting Star: rejection of higher prices, potential bearish reversal.")
        }
        return null
    }

    private fun detectDoji(c: Candle): PatternResult? {
        val body = abs(c.close - c.open)
        val range = c.high - c.low
        if (range == 0.0) return null
        // Doji: body is very small relative to range (< 5%)
        if (body / range < 0.05) {
            return PatternResult("Doji", true, "weak",
                "Doji: indecision candle, neutral bias.")
        }
        return null
    }

    private fun detectMorningStar(c1: Candle, c2: Candle, c3: Candle): PatternResult? {
        val c1Body = abs(c1.close - c1.open)
        val c2Body = abs(c2.close - c2.open)
        val c3Body = abs(c3.close - c3.open)
        // Bearish c1, small c2 (star), bullish c3 that closes above c1 midpoint
        if (c1.close < c1.open && c3.close > c3.open &&
            c2Body < c1Body * 0.5 && c3.close > c1.open + c1Body * 0.5) {
            return PatternResult("Morning Star", true, "strong",
                "Morning Star: three-candle bullish reversal pattern.")
        }
        return null
    }

    private fun detectEveningStar(c1: Candle, c2: Candle, c3: Candle): PatternResult? {
        val c1Body = abs(c1.close - c1.open)
        val c2Body = abs(c2.close - c2.open)
        val c3Body = abs(c3.close - c3.open)
        // Bullish c1, small c2 (star), bearish c3 that closes below c1 midpoint
        if (c1.close > c1.open && c3.close < c3.open &&
            c2Body < c1Body * 0.5 && c3.close < c1.open - c1Body * 0.5) {
            return PatternResult("Evening Star", false, "strong",
                "Evening Star: three-candle bearish reversal pattern.")
        }
        return null
    }

    private fun detectPiercingLine(c2: Candle, c3: Candle): PatternResult? {
        // Bearish c2, bullish c3, c3 opens below c2 low and closes above c2 midpoint
        val c2Mid = (c2.open + c2.close) / 2.0
        if (c2.close < c2.open && c3.close > c3.open &&
            c3.open < c2.low && c3.close > c2Mid) {
            return PatternResult("Piercing Line", true, "moderate",
                "Piercing Line: bullish reversal after a down candle.")
        }
        return null
    }

    private fun detectDarkCloudCover(c2: Candle, c3: Candle): PatternResult? {
        // Bullish c2, bearish c3, c3 opens above c2 high and closes below c2 midpoint
        val c2Mid = (c2.open + c2.close) / 2.0
        if (c2.close > c2.open && c3.close < c3.open &&
            c3.open > c2.high && c3.close < c2Mid) {
            return PatternResult("Dark Cloud Cover", false, "moderate",
                "Dark Cloud Cover: bearish reversal after an up candle.")
        }
        return null
    }

    private fun minVal(a: Double, b: Double) = if (a < b) a else b
    private fun maxVal(a: Double, b: Double) = if (a > b) a else b
}
