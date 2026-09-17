package org.traccar.client

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackingProfilePolicyTest {

    private val config = AdaptiveTrackingConfig(enabled = true)

    @Test
    fun chargingHasHighestPriority() {
        val profile = selectTrackingProfile(
            ProfileInputs(
                motion = MotionActivity.VEHICLE,
                batteryPercent = 10,
                charging = true,
                speedMps = 20.0,
            ),
            config,
        )

        assertEquals(TrackingProfile.CHARGING, profile)
    }

    @Test
    fun lowBatteryUsesBatterySaverWhenNotCharging() {
        val profile = selectTrackingProfile(
            ProfileInputs(
                motion = MotionActivity.VEHICLE,
                batteryPercent = config.lowBatteryThresholdPercent,
                charging = false,
                speedMps = 20.0,
            ),
            config,
        )

        assertEquals(TrackingProfile.BATTERY_SAVER, profile)
    }

    @Test
    fun vehicleMotionSelectsDriving() {
        assertEquals(
            TrackingProfile.DRIVING,
            selectTrackingProfile(ProfileInputs(motion = MotionActivity.VEHICLE), config),
        )
    }

    @Test
    fun speedUsesHysteresisWhileAlreadyDriving() {
        assertEquals(
            TrackingProfile.DRIVING,
            selectTrackingProfile(
                ProfileInputs(
                    current = TrackingProfile.DRIVING,
                    motion = MotionActivity.UNKNOWN,
                    speedMps = 3.0,
                ),
                config,
            ),
        )
        assertEquals(
            TrackingProfile.DEFAULT,
            selectTrackingProfile(
                ProfileInputs(
                    current = TrackingProfile.DRIVING,
                    motion = MotionActivity.UNKNOWN,
                    speedMps = 1.0,
                ),
                config,
            ),
        )
    }

    @Test
    fun walkingAndConfirmedStationaryProfilesAreSelected() {
        assertEquals(
            TrackingProfile.WALKING,
            selectTrackingProfile(ProfileInputs(motion = MotionActivity.WALKING), config),
        )
        assertEquals(
            TrackingProfile.STATIONARY,
            selectTrackingProfile(ProfileInputs(stationaryConfirmed = true), config),
        )
    }

    @Test
    fun profileLocationConfigAppliesExpectedOverrides() {
        val base = LocationConfig(
            accuracy = Accuracy.MEDIUM,
            distanceMeters = 75,
            intervalSeconds = 300,
            heartbeatIntervalSeconds = 900,
        )

        assertEquals(
            base.copy(accuracy = Accuracy.HIGH, distanceMeters = config.drivingDistanceMeters, intervalSeconds = 0),
            locationConfigForProfile(base, TrackingProfile.DRIVING, config),
        )
        assertEquals(
            base.copy(accuracy = Accuracy.HIGH, distanceMeters = config.walkingDistanceMeters, intervalSeconds = 0),
            locationConfigForProfile(base, TrackingProfile.WALKING, config),
        )
        assertEquals(
            base.copy(accuracy = Accuracy.HIGH, distanceMeters = 0, intervalSeconds = config.chargingIntervalSeconds),
            locationConfigForProfile(base, TrackingProfile.CHARGING, config),
        )
        assertEquals(
            base.copy(accuracy = Accuracy.LOW, distanceMeters = config.batterySaverDistanceMeters, intervalSeconds = 0),
            locationConfigForProfile(base, TrackingProfile.BATTERY_SAVER, config),
        )
        assertEquals(
            base.copy(accuracy = Accuracy.MEDIUM, distanceMeters = 0, intervalSeconds = 300),
            locationConfigForProfile(base, TrackingProfile.STATIONARY, config),
        )
    }
}
