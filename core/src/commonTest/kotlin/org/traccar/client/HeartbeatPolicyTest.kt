package org.traccar.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeartbeatPolicyTest {

    @Test
    fun freshCandidateIsPreserved() {
        val now = 1_000_000L
        val candidate = Position(
            latitude = 20.2501,
            longitude = 105.9742,
            accuracy = 8.0,
            time = now - 119_000L,
        )

        assertEquals(
            candidate,
            resolveHeartbeatPosition(candidate, nowMillis = now, maxAgeSeconds = 120),
        )
    }

    @Test
    fun staleCandidateBecomesMetadataOnlyHeartbeat() {
        val now = 1_000_000L
        val candidate = Position(
            latitude = 20.2501,
            longitude = 105.9742,
            accuracy = 8.0,
            time = now - 121_000L,
        )

        val resolved = resolveHeartbeatPosition(candidate, nowMillis = now, maxAgeSeconds = 120)

        assertEquals(now, resolved.time)
        assertNull(resolved.latitude)
        assertNull(resolved.longitude)
        assertNull(resolved.accuracy)
    }

    @Test
    fun missingCandidateBecomesMetadataOnlyHeartbeat() {
        val now = 1_000_000L

        val resolved = resolveHeartbeatPosition(null, nowMillis = now, maxAgeSeconds = 120)

        assertEquals(Position(time = now), resolved)
    }

    @Test
    fun zeroMaxAgePreservesLegacyCachedPositionBehavior() {
        val now = 1_000_000L
        val candidate = Position(
            latitude = 20.2501,
            longitude = 105.9742,
            time = 1L,
        )

        assertEquals(
            candidate,
            resolveHeartbeatPosition(candidate, nowMillis = now, maxAgeSeconds = 0),
        )
    }
}
