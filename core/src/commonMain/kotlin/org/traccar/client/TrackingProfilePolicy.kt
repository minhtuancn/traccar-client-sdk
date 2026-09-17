package org.traccar.client

enum class TrackingProfile {
    DEFAULT,
    DRIVING,
    WALKING,
    STATIONARY,
    CHARGING,
    BATTERY_SAVER,
}

enum class MotionActivity {
    UNKNOWN,
    STILL,
    WALKING,
    RUNNING,
    CYCLING,
    VEHICLE,
}

data class ProfileInputs(
    val current: TrackingProfile = TrackingProfile.DEFAULT,
    val motion: MotionActivity = MotionActivity.UNKNOWN,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val speedMps: Double? = null,
    val stationaryConfirmed: Boolean = false,
)

internal fun selectTrackingProfile(
    inputs: ProfileInputs,
    config: AdaptiveTrackingConfig,
): TrackingProfile {
    if (!config.enabled) return TrackingProfile.DEFAULT

    if (inputs.charging == true) return TrackingProfile.CHARGING

    if (inputs.charging != true &&
        inputs.batteryPercent != null &&
        inputs.batteryPercent <= config.lowBatteryThresholdPercent
    ) {
        return TrackingProfile.BATTERY_SAVER
    }

    if (inputs.stationaryConfirmed) return TrackingProfile.STATIONARY

    if (inputs.motion == MotionActivity.VEHICLE || inputs.motion == MotionActivity.CYCLING) {
        return TrackingProfile.DRIVING
    }

    val speed = inputs.speedMps
    if (speed != null) {
        if (speed >= config.drivingEnterSpeedMps) return TrackingProfile.DRIVING
        if (inputs.current == TrackingProfile.DRIVING && speed >= config.drivingExitSpeedMps) {
            return TrackingProfile.DRIVING
        }
    }

    if (inputs.motion == MotionActivity.WALKING || inputs.motion == MotionActivity.RUNNING) {
        return TrackingProfile.WALKING
    }

    return TrackingProfile.DEFAULT
}

internal fun locationConfigForProfile(
    base: LocationConfig,
    profile: TrackingProfile,
    config: AdaptiveTrackingConfig,
): LocationConfig = when (profile) {
    TrackingProfile.DEFAULT -> base
    TrackingProfile.DRIVING -> base.copy(
        accuracy = Accuracy.HIGH,
        distanceMeters = config.drivingDistanceMeters,
        intervalSeconds = 0,
    )
    TrackingProfile.WALKING -> base.copy(
        accuracy = Accuracy.HIGH,
        distanceMeters = config.walkingDistanceMeters,
        intervalSeconds = 0,
    )
    TrackingProfile.STATIONARY -> base.copy(
        accuracy = Accuracy.MEDIUM,
        distanceMeters = 0,
        intervalSeconds = 300,
    )
    TrackingProfile.CHARGING -> base.copy(
        accuracy = Accuracy.HIGH,
        distanceMeters = 0,
        intervalSeconds = config.chargingIntervalSeconds,
    )
    TrackingProfile.BATTERY_SAVER -> base.copy(
        accuracy = Accuracy.LOW,
        distanceMeters = config.batterySaverDistanceMeters,
        intervalSeconds = 0,
    )
}
