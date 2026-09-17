package org.traccar.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncTelemetryTest {

    @Test
    fun stateStartsWithoutSuccessfulSyncTimestamp() {
        assertNull(State().lastSuccessfulSyncMillis)
    }

    @Test
    fun stateKeepsSuccessfulSyncTimestamp() {
        val state = State(lastSuccessfulSyncMillis = 1_726_553_600_000L)

        assertEquals(1_726_553_600_000L, state.lastSuccessfulSyncMillis)
    }
}
