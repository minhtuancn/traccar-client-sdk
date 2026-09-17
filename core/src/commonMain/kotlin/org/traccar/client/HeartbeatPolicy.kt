package org.traccar.client

/**
 * Resolves the location payload used for a stationary heartbeat.
 *
 * `fetchOnce()` asks the platform for a fresh fix but can fall back to a
 * cached location. A positive [maxAgeSeconds] prevents that cache fallback
 * from presenting an arbitrarily old coordinate as a fresh heartbeat. When a
 * candidate is missing or stale, the heartbeat is still uploaded without
 * coordinates; the Traccar OsmAnd decoder then keeps the device's last known
 * location while recording the new heartbeat timestamp and attributes.
 *
 * Set [maxAgeSeconds] to `0` to preserve the legacy behavior and accept any
 * cached candidate.
 */
internal fun resolveHeartbeatPosition(
    candidate: Position?,
    nowMillis: Long,
    maxAgeSeconds: Int,
): Position {
    if (candidate == null) return Position(time = nowMillis)
    if (maxAgeSeconds <= 0) return candidate

    val ageMillis = (nowMillis - candidate.time).coerceAtLeast(0L)
    return if (ageMillis <= maxAgeSeconds * 1000L) {
        candidate
    } else {
        Position(time = nowMillis)
    }
}
