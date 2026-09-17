import 'package:flutter/services.dart';

/// Location-accuracy preset. Maps to the SDK's `Accuracy` enum.
enum Accuracy { highest, high, medium, low }

enum SyncMode { instant, batch, offline }

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

/// Durable queue draining policy. Batch mode still sends normal Traccar
/// requests one by one; it only controls when and how many queued points drain.
class SmartSyncConfig {
  const SmartSyncConfig({
    this.enabled = false,
    this.mode = SyncMode.instant,
    this.batchSize = 25,
    this.batchIntervalSeconds = 60,
  });

  final bool enabled;
  final SyncMode mode;
  final int batchSize;
  final int batchIntervalSeconds;

  Map<String, Object?> _toMap() => {
        'enabled': enabled,
        'mode': mode.name.toUpperCase(),
        'batchSize': batchSize,
        'batchIntervalSeconds': batchIntervalSeconds,
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
    this.smartSync = const SmartSyncConfig(),
    this.wakeLock = false,
    this.buffer = true,
    this.preferPlatformProviders = false,
    this.notification = const NotificationConfig(),
  });

  final String serverUrl;
  final String deviceId;
  final LocationConfig location;
  final AdaptiveTrackingConfig adaptiveTracking;
  final SmartSyncConfig smartSync;
  final bool wakeLock;
  final bool buffer;
  final bool preferPlatformProviders;
  final NotificationConfig notification;

  Map<String, Object?> _toMap() => {
        'serverUrl': serverUrl,
        'deviceId': deviceId,
        'location': location._toMap(),
        'adaptiveTracking': adaptiveTracking._toMap(),
        'smartSync': smartSync._toMap(),
        'wakeLock': wakeLock,
        'buffer': buffer,
        'preferPlatformProviders': preferPlatformProviders,
        'notification': notification._toMap(),
      };
}

/// A single diagnostic log entry.
class LogEntry {
  const LogEntry({required this.time, required this.message});

  final int time;
  final String message;
}

/// Entry point for the Traccar Client SDK Flutter plugin.
class TraccarClientSdk {
  static const MethodChannel _channel = MethodChannel('traccar_client_sdk');

  Future<void> init(Config config) =>
      _channel.invokeMethod<void>('init', config._toMap());

  Future<void> setConfig(Config config) =>
      _channel.invokeMethod<void>('setConfig', config._toMap());

  Future<void> start() => _channel.invokeMethod<void>('start');

  Future<void> stop() => _channel.invokeMethod<void>('stop');

  /// Force-drains the durable queue. In offline sync mode this is the only
  /// operation that uploads buffered points.
  Future<void> syncNow() => _channel.invokeMethod<void>('syncNow');

  Future<bool> requestPosition({String? alarm}) async {
    final result = await _channel.invokeMethod<bool>(
      'requestPosition',
      {'alarm': alarm},
    );
    return result ?? false;
  }

  Future<bool> isTracking() async {
    final result = await _channel.invokeMethod<bool>('isTracking');
    return result ?? false;
  }

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

  Future<void> clearLogs() => _channel.invokeMethod<void>('clearLogs');
}
