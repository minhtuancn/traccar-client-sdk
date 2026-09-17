package org.traccar.client

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val serverUrl: String,
    val deviceId: String,
    val location: LocationConfig = LocationConfig(),
    val adaptiveTracking: AdaptiveTrackingConfig = AdaptiveTrackingConfig(),
    val smartSync: SmartSyncConfig = SmartSyncConfig(),
    val wakeLock: Boolean = false,
    val buffer: Boolean = true,
    val preferPlatformProviders: Boolean = false,
    val notification: NotificationConfig = NotificationConfig(),
) {
    constructor(serverUrl: String, deviceId: String) : this(serverUrl, deviceId, LocationConfig())
}

@Serializable
data class NotificationConfig(
    val text: String = "Location tracking",
)

@Serializable
data class LocationConfig(
    val accuracy: Accuracy = Accuracy.MEDIUM,
    val distanceMeters: Int = 75,
    val intervalSeconds: Int = 300,
    val angleDegrees: Int = 0,
    val stopDetection: Boolean = true,
    val stopTimeoutSeconds: Int = 60,
    val stationaryRadiusMeters: Int = 100,
    val heartbeatIntervalSeconds: Int = 0,
    val heartbeatMaxAgeSeconds: Int = 300,
)

@Serializable
data class AdaptiveTrackingConfig(
    val enabled: Boolean = false,
    val transitionDelaySeconds: Int = 10,
    val lowBatteryThresholdPercent: Int = 20,
    val drivingEnterSpeedMps: Double = 4.2,
    val drivingExitSpeedMps: Double = 2.0,
    val drivingDistanceMeters: Int = 10,
    val walkingDistanceMeters: Int = 20,
    val chargingIntervalSeconds: Int = 10,
    val batterySaverDistanceMeters: Int = 100,
)

@Serializable
data class SmartSyncConfig(
    val enabled: Boolean = false,
    val mode: SyncMode = SyncMode.INSTANT,
    val batchSize: Int = 25,
    val batchIntervalSeconds: Int = 60,
)

@Serializable
enum class SyncMode {
    INSTANT,
    BATCH,
    OFFLINE,
}

@Serializable
enum class Accuracy {
    HIGHEST,
    HIGH,
    MEDIUM,
    LOW,
}

val LocationConfig.effective: LocationConfig
    get() = when {
        accuracy == Accuracy.HIGHEST -> copy(distanceMeters = 0, intervalSeconds = 0)
        distanceMeters > 0 -> copy(intervalSeconds = 0)
        else -> this
    }
