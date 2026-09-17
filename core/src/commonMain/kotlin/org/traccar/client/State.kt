package org.traccar.client

import kotlinx.serialization.Serializable

@Serializable
data class State(
    val enabled: Boolean = false,
    val paused: Boolean = false,
    val lastAcceptedLocation: Position? = null,
    val lastSuccessfulSyncMillis: Long? = null,
)
