package org.traccar.client

internal fun shouldAutoSync(config: SmartSyncConfig): Boolean =
    !config.enabled || config.mode != SyncMode.OFFLINE

internal fun automaticDrainLimit(config: SmartSyncConfig): Int = when {
    !config.enabled -> 1
    config.mode == SyncMode.INSTANT -> 1
    config.mode == SyncMode.BATCH -> config.batchSize.coerceAtLeast(1)
    else -> 0
}

internal fun manualDrainLimit(): Int = Int.MAX_VALUE
