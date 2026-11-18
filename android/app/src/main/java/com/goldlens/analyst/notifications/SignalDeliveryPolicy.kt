package com.goldlens.analyst.notifications

import com.goldlens.analyst.core.Direction

/** Alert only when a symbol changes into a new directional state; repeated BUY/SELL bars are deduplicated. */
object SignalDeliveryPolicy {
    fun shouldDeliver(previousDirection: String?, currentDirection: Direction): Boolean =
        (currentDirection == Direction.BUY || currentDirection == Direction.SELL) && previousDirection != currentDirection.name
}
