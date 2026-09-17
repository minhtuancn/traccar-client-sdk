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

class TrackerEngine internal constructor(
    private val stateStore: StateStore,
    private val queue: PositionQueue,
    private val network: NetworkMonitor,
    private val locationSource: LocationSource,
    signalSources: List<SignalSource>,
    private val processors: List<PositionProcessor>,
    private val uploader: Uploader,
    private val buffer: Boolean,
    scope: ComponentCoroutineScope,
    private val heartbeatMaxAgeSeconds: Int = 120,
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
        val candidate = locationSource.fetchOnce()
        val position = resolveHeartbeatPosition(
            candidate = candidate,
            nowMillis = now,
            maxAgeSeconds = heartbeatMaxAgeSeconds,
        )
        if (candidate != null && candidate.latitude != null && position.latitude == null) {
            Log.log("Heartbeat location stale; using last server location")
        } else if (candidate == null) {
            Log.log("Heartbeat location unavailable; using last server location")
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
