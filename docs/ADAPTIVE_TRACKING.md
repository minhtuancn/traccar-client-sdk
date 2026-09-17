# Adaptive Tracking Profiles

This fork adds an optional runtime profile engine on top of the existing Traccar Client SDK location pipeline. The feature reuses the existing foreground service, stop detection, location sources, durable queue, uploader, and heartbeat infrastructure.

## Profiles

| Profile | Trigger | Location policy |
| --- | --- | --- |
| `DEFAULT` | No stronger condition | Base `LocationConfig` |
| `DRIVING` | Vehicle/cycling activity or speed above the driving threshold | High accuracy, distance based updates (default 10 m) |
| `WALKING` | Walking/running activity | High accuracy, distance based updates (default 20 m) |
| `STATIONARY` | Confirmed stationary state | Medium accuracy for one-shot/heartbeat fixes; continuous updates remain paused by stop detection |
| `CHARGING` | Device is charging | High accuracy, time based updates (default 10 s) |
| `BATTERY_SAVER` | Battery at/below threshold and not charging | Low accuracy, distance based updates (default 100 m) |

Power-state profiles intentionally have higher policy priority than motion profiles. Charging wins over low-battery and motion conditions. Low-battery wins over motion while the device is not charging.

## Hysteresis and debounce

Driving can be entered by activity recognition or by speed. The default speed thresholds are 4.2 m/s to enter and 2.0 m/s to exit, so small GPS speed fluctuations do not immediately drop the driving profile.

Motion-driven profile changes are delayed by `transitionDelaySeconds` (default 10 seconds). Confirmed stationary transitions and power-state transitions are applied immediately.

## Configuration

```kotlin
val config = Config(
    serverUrl = "https://example.com",
    deviceId = "device-1",
    adaptiveTracking = AdaptiveTrackingConfig(
        enabled = true,
        transitionDelaySeconds = 10,
        lowBatteryThresholdPercent = 20,
        drivingEnterSpeedMps = 4.2,
        drivingExitSpeedMps = 2.0,
        drivingDistanceMeters = 10,
        walkingDistanceMeters = 20,
        chargingIntervalSeconds = 10,
        batterySaverDistanceMeters = 100,
    ),
)
```

The Flutter and React Native wrappers expose the same fields. Adaptive tracking is disabled by default at SDK level for backward compatibility.

## Runtime behavior

`TrackingProfileController` exposes two state flows:

- `profile`: the active `TrackingProfile`.
- `locationConfig`: the effective `LocationConfig` after profile overrides.

Android platform and Fused location providers, and the iOS CoreLocation provider, observe the effective config and restart their location subscription only when the effective profile configuration changes.

Activity recognition remains useful even when stop detection is disabled. In that case the detector emits motion classification signals but does not emit stationary pause/resume signals.

## Fresh heartbeat interaction

Stationary heartbeat continues to use the existing heartbeat pipeline. `heartbeatMaxAgeSeconds` protects against reporting an arbitrarily stale cached coordinate as a fresh heartbeat location. Profile changes do not create a second heartbeat service or queue.

## License boundary

Colota is used only as an architecture and behavior reference. No Colota source code is copied into this Apache-2.0 fork.
