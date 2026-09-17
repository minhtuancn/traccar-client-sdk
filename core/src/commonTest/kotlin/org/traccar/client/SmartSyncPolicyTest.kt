package org.traccar.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartSyncPolicyTest {

    @Test
    fun instantModeDrainsOnePositionPerAutomaticPass() {
        val config = SmartSyncConfig(enabled = true, mode = SyncMode.INSTANT)

        assertTrue(shouldAutoSync(config))
        assertEquals(1, automaticDrainLimit(config))
    }

    @Test
    fun batchModeUsesConfiguredBurstSize() {
        val config = SmartSyncConfig(
            enabled = true,
            mode = SyncMode.BATCH,
            batchSize = 25,
        )

        assertTrue(shouldAutoSync(config))
        assertEquals(25, automaticDrainLimit(config))
    }

    @Test
    fun batchSizeIsClampedToAtLeastOne() {
        val config = SmartSyncConfig(
            enabled = true,
            mode = SyncMode.BATCH,
            batchSize = 0,
        )

        assertEquals(1, automaticDrainLimit(config))
    }

    @Test
    fun offlineModeNeverDrainsAutomatically() {
        val config = SmartSyncConfig(enabled = true, mode = SyncMode.OFFLINE)

        assertFalse(shouldAutoSync(config))
        assertEquals(0, automaticDrainLimit(config))
    }

    @Test
    fun disabledSmartSyncPreservesLegacyInstantBehavior() {
        val config = SmartSyncConfig(enabled = false, mode = SyncMode.BATCH, batchSize = 50)

        assertTrue(shouldAutoSync(config))
        assertEquals(1, automaticDrainLimit(config))
    }

    @Test
    fun manualSyncAlwaysAllowsFullDrain() {
        assertEquals(Int.MAX_VALUE, manualDrainLimit())
    }
}
