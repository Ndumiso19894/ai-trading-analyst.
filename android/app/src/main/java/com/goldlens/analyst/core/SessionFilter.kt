package com.goldlens.analyst.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Forex session filter. Signals are only considered high-quality during active sessions.
 * All times are UTC.
 *
 * Sessions:
 *   Asian:   00:00 - 08:00 UTC
 *   London:  08:00 - 17:00 UTC
 *   New York:13:00 - 22:00 UTC
 *   Overlap (London+NY): 13:00 - 17:00 UTC — highest liquidity
 */
object SessionFilter {

    enum class Session { ASIAN, LONDON, NEW_YORK, OVERLAP, CLOSED }

    data class SessionInfo(
        val session: Session,
        val isActive: Boolean,
        val description: String,
    )

    /** Returns the current session and whether it's active for trading. */
    fun currentSession(nowMillis: Long = System.currentTimeMillis()): SessionInfo {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.of("UTC"))
        val hour = zdt.hour

        return when {
            hour in 13 until 17 -> SessionInfo(Session.OVERLAP, true,
                "London + New York overlap — highest liquidity, best for breakout signals.")
            hour in 8 until 17 -> SessionInfo(Session.LONDON, true,
                "London session — active for EUR, GBP pairs.")
            hour in 13 until 22 -> SessionInfo(Session.NEW_YORK, true,
                "New York session — active for USD pairs.")
            hour in 0 until 8 -> SessionInfo(Session.ASIAN, true,
                "Asian session — lower volatility, suitable for range strategies.")
            else -> SessionInfo(Session.CLOSED, false,
                "Market closed — avoid new entries.")
        }
    }

    /** Whether the given instrument should be traded in the current session. */
    fun isSessionFavorable(instrument: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val info = currentSession(nowMillis)
        if (!info.isActive) return false

        val quote = instrument.substringAfter('/')
        val base = instrument.substringBefore('/')

        return when (info.session) {
            Session.OVERLAP -> true // All pairs tradable during overlap
            Session.LONDON -> listOf("EUR", "GBP", "CHF").contains(quote) || listOf("EUR", "GBP", "CHF").contains(base)
            Session.NEW_YORK -> quote == "USD" || base == "USD"
            Session.ASIAN -> listOf("JPY", "AUD", "NZD").contains(quote) || listOf("JPY", "AUD", "NZD").contains(base)
            Session.CLOSED -> false
        }
    }
}
