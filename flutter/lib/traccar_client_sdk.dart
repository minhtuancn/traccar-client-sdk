import 'package:flutter/services.dart';

/// Location-accuracy preset. Maps to the SDK's `Accuracy` enum.
enum Accuracy { highest, high, medium, low }

/// Tuning parameters for the location pipeline.
class LocationConfig {
  const LocationConfig({
    this.accuracy = Accuracy.medium,
    this.distanceMeters = 75,
    this.intervalSeconds = 300,
    this.angleDegrees = 0,
    this.stopDetection = true,
    this.stopTimeoutSeconds = 60,
    this.stationaryRadiusMeters = 100,
    this.heartbeatIntervalSeconds = 0,
    this.heartbeatMaxAgeSeconds = 300,
  });

  final Accuracy accuracy;
  final int distanceMeters;
  final int intervalSeconds;
  final int angleDegrees;
  final bool stopDetection;
  final int stopTimeoutSeconds;
  final int stationaryRadiusMeters;
  final int heartbeatIntervalSeconds;

  /// Maximum age of a cached heartbeat position. A stale cached coordinate is
  /// replaced by a liveness-only heartbeat. Set to 0 to disable age filtering.
  final int heartbeatMaxAgeSeconds;

  Map<String, Object?> _toMap() => {
        'accuracy': accuracy.name.toUpperCase(),
        'distanceMeters': distanceMeters,
        'intervalSeconds': intervalSeconds,
        'angleDegrees': angleDegrees,
        'stopDetection': stopDetection,
        'stopTimeoutSeconds': stopTimeoutSeconds,
        'stationaryRadiusMeters': stationaryRadiusMeters,
        'heartbeatIntervalSeconds': heartbeatIntervalSeconds,
        'heartbeatMaxAgeSeconds': heartbeatMaxAgeSeconds,
      };
}

/// Adaptive runtime profile configuration.
class AdaptiveTrackingConfig {
  const AdaptiveTrackingConfig({
    this.enabled = false,
    this.transitionDelaySeconds = 10,
    this.lowBatteryThresholdPercent = 20,
    this.drivingEnterSpeedMps = 4.2,
    this.drivingExitSpeedMps = 2.0,
    this.drivingDistanceMeters = 10,
    this.walkingDistanceMeters = 20,
    this.chargingIntervalSeconds = 10,
    this.batterySaverDistanceMeters = 100,
  });

  final bool enabled;
  final int transitionDelaySeconds;
  final int lowBatteryThresholdPercent;
  final double drivingEnterSpeedMps;
  final double drivingExitSpeedMps;
  final int drivingDistanceMeters;
  final int walkingDistanceMeters;
  final int chargingIntervalSeconds;
  final int batterySaverDistanceMeters;

  Map<String, Object?> _toMap() => {
        'enabled': enabled,
        'transitionDelaySeconds': transitionDelaySeconds,
        'lowBatteryThresholdPercent': lowBatteryThresholdPercent,
        'drivingEnterSpeedMps': drivingEnterSpeedMps,
        'drivingExitSpeedMps': drivingExitSpeedMps,
        'drivingDistanceMeters': drivingDistanceMeters,
        'walkingDistanceMeters': walkingDistanceMeters,
        'chargingIntervalSeconds': chargingIntervalSeconds,
        'batterySaverDistanceMeters': batterySaverDistanceMeters,
      };
}

/// Foreground-service notification settings (Android only).
class NotificationConfig {
  const NotificationConfig({this.text = 'Location tracking'});

  final String text;

  Map<String, Object?> _toMap() => {'text': text};
}

/// Tracker configuration. Pass to [TraccarClientSdk.setConfig] to install or
/// update.
class Config {
  const Config({
    required this.serverUrl,
    required this.deviceId,
    this.location = const LocationConfig(),
    this.adaptiveTracking = const AdaptiveTrackingConfig(),
    this.wakeLock = false,
    this.buffer = true,
    this.preferPlatformProviders = false,
    this.notification = const NotificationConfig(),
  });

  final String serverUrl;
  final String deviceId;
  final LocationConfig location;
  final AdaptiveTrackingConfig adaptiveTracking;

  /// Hold a wakelock while tracking (Android only).
  final bool wakeLock;

  /// When true, persist positions to a local queue and retry on network
  /// failure. When false, attempt a direct upload for each position and
  /// drop it on failure (real-time only).
  final bool buffer;

  /// When true, the Android SDK uses the platform `LocationManager` directly
  /// even when Google Play Services is available. Default `false` picks the
  /// Fused Location Provider when Play Services is present. Ignored on iOS.
  final bool preferPlatformProviders;

  final NotificationConfig notification;

  Map<String, Object?> _toMap() => {
        'serverUrl': serverUrl,
        'deviceId': deviceId,
        'location': location._toMap(),
        'adaptiveTracking': adaptiveTracking._toMap(),
        'wakeLock': wakeLock,
        'buffer': buffer,
        'preferPlatformProviders': preferPlatformProviders,
        'notification': notification._toMap(),
      };
}

/// A single diagnostic log entry.
class LogEntry {
  const LogEntry({required this.time, required this.message});

  /// Epoch milliseconds at which the entry was recorded.
  final int time;
  final String message;
}

/// Entry point for the Traccar Client SDK Flutter plugin.
class TraccarClientSdk {
  static const MethodChannel _channel = MethodChannel('traccar_client_sdk');

  /// Initializes the SDK with [config] if it isn't already initialized.
  /// Idempotent — subsequent calls return the existing tracker without
  /// touching its config. Call once at app startup to seed defaults; use
  /// [setConfig] to change settings on a running tracker.
  Future<void> init(Config config) =>
      _channel.invokeMethod<void>('init', config._toMap());

  /// Replaces the running tracker's configuration with [config]. Requires
  /// that [init] (or a prior session) has installed a tracker. Throws a
  /// [PlatformException] otherwise.
  Future<void> setConfig(Config config) =>
      _channel.invokeMethod<void>('setConfig', config._toMap());

  /// Starts background location tracking. Requires that the SDK has been
  /// initialized (via [init] in this session, or via persisted config from
  /// a previous one). Throws a [PlatformException] if required permissions
  /// were denied or no config has ever been provided.
  Future<void> start() => _channel.invokeMethod<void>('start');

  /// Stops tracking.
  Future<void> stop() => _channel.invokeMethod<void>('stop');

  /// Requests a single position fix and uploads it to the server. Returns
  /// whether the upload succeeded. Works independently of [start] / [stop].
  Future<bool> requestPosition({String? alarm}) async {
    final result = await _channel.invokeMethod<bool>(
      'requestPosition',
      {'alarm': alarm},
    );
    return result ?? false;
  }

  /// Returns whether tracking is currently active.
  Future<bool> isTracking() async {
    final result = await _channel.invokeMethod<bool>('isTracking');
    return result ?? false;
  }

  /// Returns recent diagnostic entries, oldest first.
  Future<List<LogEntry>> getLogs() async {
    final raw =
        await _channel.invokeListMethod<Map<dynamic, dynamic>>('getLogs');
    if (raw == null) return const [];
    return raw
        .map((m) => LogEntry(
              time: m['time'] as int,
              message: m['message'] as String,
            ))
        .toList(growable: false);
  }

  /// Clears all stored diagnostic entries.
  Future<void> clearLogs() => _channel.invokeMethod<void>('clearLogs');
}
