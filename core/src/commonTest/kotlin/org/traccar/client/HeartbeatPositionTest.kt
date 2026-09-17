package org.traccar.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeartbeatPositionTest {

    @Test
    fun keepsFreshPosition() {
        val now = 1_000_000L
        val source = Position(
            latitude = 20.123,
            longitude = 106.456,
            accuracy = 8.0,
            time = now - 60_000L,
        )

        val result = heartbeatPosition(source, now, maxAgeSeconds = 300)

        assertEquals(source, result)
    }

    @Test
    fun stripsCoordinatesFromStalePosition() {
        val now = 1_000_000L
        val source = Position(
            latitude = 20.123,
            longitude = 106.456,
            accuracy = 8.0,
            time = now - 301_000L,
        )

        val result = heartbeatPosition(source, now, maxAgeSeconds = 300)

        assertEquals(now, result.time)
        assertNull(result.latitude)
        assertNull(result.longitude)
        assertNull(result.accuracy)
    }

    @Test
    fun createsLivenessOnlyHeartbeatWhenNoPositionExists() {
        val now = 1_000_000L

        val result = heartbeatPosition(null, now, maxAgeSeconds = 300)

        assertEquals(now, result.time)
        assertNull(result.latitude)
        assertNull(result.longitude)
    }

    @Test
    fun zeroMaxAgeDisablesStalePositionFiltering() {
        val now = 1_000_000L
        val source = Position(
            latitude = 20.123,
            longitude = 106.456,
            time = 1L,
        )

        val result = heartbeatPosition(source, now, maxAgeSeconds = 0)

        assertEquals(source, result)
    }
}
