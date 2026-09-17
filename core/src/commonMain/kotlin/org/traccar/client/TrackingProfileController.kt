package org.traccar.client

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TrackingProfileController internal constructor(
    config: Config,
    private val scope: ComponentCoroutineScope,
) {
    private val adaptive = config.adaptiveTracking
    private val baseLocation = config.location
    private val mutex = Mutex()

    private val _profile = MutableStateFlow(TrackingProfile.DEFAULT)
    val profile: StateFlow<TrackingProfile> = _profile.asStateFlow()

    private val _locationConfig = MutableStateFlow(baseLocation)
    val locationConfig: StateFlow<LocationConfig> = _locationConfig.asStateFlow()

    private var motion = MotionActivity.UNKNOWN
    private var batteryPercent: Int? = null
    private var charging: Boolean? = null
    private var speedMps: Double? = null
    private var stationaryConfirmed = false

    private var pendingProfile: TrackingProfile? = null
    private var transitionJob: Job? = null

    suspend fun handle(signal: Signal) = mutex.withLock {
        when (signal) {
            is Signal.MotionChanged -> {
                motion = signal.activity
                reevaluateLocked(immediate = false)
            }
            Signal.StationaryEnter -> {
                stationaryConfirmed = true
                reevaluateLocked(immediate = true)
            }
            Signal.StationaryExit -> {
                stationaryConfirmed = false
                reevaluateLocked(immediate = true)
            }
            Signal.HeartbeatTick -> Unit
        }
    }

    suspend fun observe(position: Position) = mutex.withLock {
        val previousCharging = charging
        val previousBattery = batteryPercent

        speedMps = position.speed?.takeIf { it.isFinite() && it >= 0.0 }
        batteryPercent = position.battery?.takeIf { it in 0..100 }
        charging = position.charging

        val powerChanged = previousCharging != charging ||
            crossedLowBatteryThreshold(previousBattery, batteryPercent)
        reevaluateLocked(immediate = powerChanged)
    }

    private fun crossedLowBatteryThreshold(previous: Int?, current: Int?): Boolean {
        if (previous == null || current == null) return previous != current
        val threshold = adaptive.lowBatteryThresholdPercent
        return (previous <= threshold) != (current <= threshold)
    }

    private fun currentInputs(): ProfileInputs = ProfileInputs(
        current = _profile.value,
        motion = motion,
        batteryPercent = batteryPercent,
        charging = charging,
        speedMps = speedMps,
        stationaryConfirmed = stationaryConfirmed,
    )

    private fun reevaluateLocked(immediate: Boolean) {
        val target = selectTrackingProfile(currentInputs(), adaptive)
        if (target == _profile.value) {
            cancelPendingLocked()
            return
        }

        val shouldApplyImmediately = immediate ||
            target == TrackingProfile.CHARGING ||
            target == TrackingProfile.BATTERY_SAVER ||
            target == TrackingProfile.STATIONARY ||
            adaptive.transitionDelaySeconds <= 0

        if (shouldApplyImmediately) {
            applyLocked(target)
            return
        }

        if (pendingProfile == target && transitionJob?.isActive == true) return
        cancelPendingLocked()
        pendingProfile = target
        transitionJob = scope.launch {
            delay(adaptive.transitionDelaySeconds.seconds)
            mutex.withLock {
                if (selectTrackingProfile(currentInputs(), adaptive) == target) {
                    applyLocked(target)
                } else {
                    cancelPendingLocked()
                }
            }
        }
    }

    private fun applyLocked(profile: TrackingProfile) {
        cancelPendingLocked()
        if (_profile.value == profile) return
        _profile.value = profile
        _locationConfig.value = locationConfigForProfile(baseLocation, profile, adaptive)
        Log.log("Tracking profile: ${profile.name.lowercase()}")
    }

    private fun cancelPendingLocked() {
        transitionJob?.cancel()
        transitionJob = null
        pendingProfile = null
    }
}
