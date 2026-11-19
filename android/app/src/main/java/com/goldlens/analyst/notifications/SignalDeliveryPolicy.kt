package com.goldlens.analyst.notifications

import com.goldlens.analyst.core.Direction
import java.time.Instant

/** Alert only when a symbol changes into a new directional state, with a time-based cooldown to avoid repeat alerts. */
object SignalDeliveryPolicy {

    /** Minimum milliseconds between repeated alerts for the same symbol/timeframe/direction. */
    private const val COOLDOWN_MILLIS = 60L * 60L * 1000L // 1 hour

    /** Last delivery timestamp per "symbol_timeframe_direction" key. */
    private val lastDelivery = mutableMapOf<String, Long>()

    fun shouldDeliver(previousDirection: String?, currentDirection: Direction): Boolean {
        if (currentDirection != Direction.BUY && currentDirection != Direction.SELL) return false
        if (previousDirection == currentDirection.name) return false
        return true
    }

    /** Time-gated delivery: true only if the cooldown has elapsed for this symbol/timeframe/direction. */
    fun shouldDeliverWithCooldown(symbol: String, timeframe: String, currentDirection: Direction): Boolean {
        if (currentDirection != Direction.BUY && currentDirection != Direction.SELL) return false
        val key = "${symbol}_${timeframe}_${currentDirection.name}"
        val now = Instant.now().toEpochMilli()
        val last = lastDelivery[key] ?: 0L
        if (now - last < COOLDOWN_MILLIS) return false
        lastDelivery[key] = now
        return true
    }

    fun resetCooldown(symbol: String, timeframe: String, direction: Direction) {
        val key = "${symbol}_${timeframe}_${direction.name}"
        lastDelivery.remove(key)
    }
}
