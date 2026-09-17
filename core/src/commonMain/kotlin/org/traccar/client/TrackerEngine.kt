package org.traccar.client

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun heartbeatPosition(
    position: Position?,
    now: Long,
    maxAgeSeconds: Int,
): Position {
    if (position == null) return Position(time = now)
    if (maxAgeSeconds <= 0) return position

    val ageMs = (now - position.time).coerceAtLeast(0L)
    val maxAgeMs = maxAgeSeconds.toLong() * 1000L
    return if (ageMs <= maxAgeMs) {
        position
    } else {
        Position(time = now)
    }
}

class TrackerEngine internal constructor(
    private val stateStore: StateStore,
    private val queue: PositionQueue,
    private val network: NetworkMonitor,
    private val locationSource: LocationSource,
    private val config: Config,
    signalSources: List<SignalSource>,
    private val processors: List<PositionProcessor>,
    private val uploader: Uploader,
    private val buffer: Boolean,
    scope: ComponentCoroutineScope,
    private val initialBackoff: Duration = 5.seconds,
    private val maxBackoff: Duration = 5.minutes,
) {
    private val mutex = Mutex()
    private val pipelineWakeUp = Channel<Unit>(Channel.CONFLATED)
    private val heartbeatPositions = MutableSharedFlow<Position>(extraBufferCapacity = 4)

    init {
        scope.launch { signalSources.map { it.signals }.merge().collect { handle(it) } }
        scope.launch { pipelineLoop() }
        scope.launch { syncLoop() }
    }

    suspend fun handle(signal: Signal) = mutex.withLock {
        when (signal) {
            Signal.StationaryEnter -> applyStationaryEnter()
            Signal.StationaryExit -> applyStationaryExit()
            Signal.HeartbeatTick -> applyHeartbeatTick()
        }
    }

    private suspend fun applyStationaryEnter() {
        val state = stateStore.state.value
        if (!state.enabled || state.paused) return
        Log.log("StationaryEnter: pausing")
        stateStore.update { it.copy(paused = true) }
    }

    private suspend fun applyStationaryExit() {
        val state = stateStore.state.value
        if (!state.enabled || !state.paused) return
        Log.log("StationaryExit: resuming")
        stateStore.update { it.copy(paused = false) }
    }

    private suspend fun applyHeartbeatTick() {
        val state = stateStore.state.value
        if (!state.enabled || !state.paused) return
        Log.log("HeartbeatTick")
        val now = Clock.System.now().toEpochMilliseconds()
        val fetched = locationSource.fetchOnce()
        val position = heartbeatPosition(
            position = fetched,
            now = now,
            maxAgeSeconds = config.location.heartbeatMaxAgeSeconds,
        )
        if (fetched != null && position.latitude == null && fetched.latitude != null) {
            Log.log("Heartbeat position stale; sending liveness-only heartbeat")
        }
        heartbeatPositions.emit(position)
    }

    private suspend fun pipelineLoop() {
        merge(locationSource.positions, heartbeatPositions).collect { incoming ->
            if (!stateStore.state.value.enabled) return@collect
            var current: Position? = incoming
            for (processor in processors) {
                current = processor.process(current ?: break)
            }
            val final = current ?: return@collect
            if (buffer) {
                queue.enqueue(final)
                pipelineWakeUp.trySend(Unit)
            } else {
                uploader.upload(final)
            }
        }
    }

    private suspend fun syncLoop() {
        var backoff = initialBackoff
        while (currentCoroutineContext().isActive) {
            val pending = queue.peek()
            when {
                pending == null -> pipelineWakeUp.receive()
                !network.isOnline.value -> {
                    Log.log("Offline, waiting for network")
                    network.isOnline.first { it }
                    Log.log("Network restored")
                }
                uploader.upload(pending) -> {
                    queue.removeFirst()
                    backoff = initialBackoff
                }
                else -> {
                    Log.log("Upload failed, retrying in $backoff")
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(maxBackoff)
                }
            }
        }
    }
}
