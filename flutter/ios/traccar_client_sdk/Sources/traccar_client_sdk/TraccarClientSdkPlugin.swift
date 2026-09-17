import Flutter
import UIKit
import TraccarClientSDK

public class TraccarClientSdkPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "traccar_client_sdk", binaryMessenger: registrar.messenger())
    let instance = TraccarClientSdkPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "init":
      let args = call.arguments as! [String: Any]
      let config = parseConfig(args)
      runHandler(result) {
        _ = try await TrackerKt.sharedTracker(config: config)
        return nil
      }
    case "setConfig":
      let args = call.arguments as! [String: Any]
      let config = parseConfig(args)
      runHandler(result) {
        guard let tracker = try await TrackerKt.sharedTracker() else {
          return FlutterError(code: "notInitialized", message: "Tracker not initialized", details: nil)
        }
        _ = try await tracker.updateConfig(newConfig: config)
        return nil
      }
    case "start":
      runHandler(result) {
        guard let tracker = try await TrackerKt.sharedTracker() else {
          return FlutterError(code: "notInitialized", message: "Tracker not initialized", details: nil)
        }
        try await tracker.start()
        return nil
      }
    case "stop":
      runHandler(result) {
        try await TrackerKt.sharedTracker()?.stop()
        return nil
      }
    case "syncNow":
      runHandler(result) {
        try await TrackerKt.sharedTracker()?.syncNow()
        return nil
      }
    case "requestPosition":
      let alarm = (call.arguments as? [String: Any])?["alarm"] as? String
      runHandler(result) {
        let uploaded = try await TrackerKt.sharedTracker()?.requestPosition(alarm: alarm)
        return uploaded?.boolValue ?? false
      }
    case "isTracking":
      runHandler(result) {
        guard let tracker = try await TrackerKt.sharedTracker() else { return false }
        return (tracker.state.value as? State)?.enabled ?? false
      }
    case "getLogs":
      runHandler(result) {
        guard let tracker = try await TrackerKt.sharedTracker() else { return [] as [[String: Any]] }
        return try await tracker.getLogs().map { ["time": $0.time, "message": $0.message] as [String: Any] }
      }
    case "clearLogs":
      runHandler(result) {
        try await TrackerKt.sharedTracker()?.clearLogs()
        return nil
      }
    default:
      result(FlutterMethodNotImplemented)
    }
  }

  private func runHandler(_ result: @escaping FlutterResult, block: @escaping () async throws -> Any?) {
    Task {
      do {
        let value = try await block()
        result(value)
      } catch {
        result(FlutterError(code: String(describing: type(of: error)), message: error.localizedDescription, details: nil))
      }
    }
  }

  private func parseConfig(_ args: [String: Any]) -> Config {
    let location = args["location"] as! [String: Any]
    let adaptive = args["adaptiveTracking"] as? [String: Any] ?? [:]
    let smartSync = args["smartSync"] as? [String: Any] ?? [:]
    let notification = args["notification"] as! [String: Any]
    return Config(
      serverUrl: args["serverUrl"] as! String,
      deviceId: args["deviceId"] as! String,
      location: LocationConfig(
        accuracy: parseAccuracy(location["accuracy"] as! String),
        distanceMeters: Int32(location["distanceMeters"] as! Int),
        intervalSeconds: Int32(location["intervalSeconds"] as! Int),
        angleDegrees: Int32(location["angleDegrees"] as! Int),
        stopDetection: location["stopDetection"] as! Bool,
        stopTimeoutSeconds: Int32(location["stopTimeoutSeconds"] as! Int),
        stationaryRadiusMeters: Int32(location["stationaryRadiusMeters"] as! Int),
        heartbeatIntervalSeconds: Int32(location["heartbeatIntervalSeconds"] as! Int),
        heartbeatMaxAgeSeconds: Int32(location["heartbeatMaxAgeSeconds"] as? Int ?? 300)
      ),
      adaptiveTracking: AdaptiveTrackingConfig(
        enabled: adaptive["enabled"] as? Bool ?? false,
        transitionDelaySeconds: Int32(adaptive["transitionDelaySeconds"] as? Int ?? 10),
        lowBatteryThresholdPercent: Int32(adaptive["lowBatteryThresholdPercent"] as? Int ?? 20),
        drivingEnterSpeedMps: adaptive["drivingEnterSpeedMps"] as? Double ?? 4.2,
        drivingExitSpeedMps: adaptive["drivingExitSpeedMps"] as? Double ?? 2.0,
        drivingDistanceMeters: Int32(adaptive["drivingDistanceMeters"] as? Int ?? 10),
        walkingDistanceMeters: Int32(adaptive["walkingDistanceMeters"] as? Int ?? 20),
        chargingIntervalSeconds: Int32(adaptive["chargingIntervalSeconds"] as? Int ?? 10),
        batterySaverDistanceMeters: Int32(adaptive["batterySaverDistanceMeters"] as? Int ?? 100)
      ),
      smartSync: SmartSyncConfig(
        enabled: smartSync["enabled"] as? Bool ?? false,
        mode: parseSyncMode(smartSync["mode"] as? String ?? "INSTANT"),
        batchSize: Int32(smartSync["batchSize"] as? Int ?? 25),
        batchIntervalSeconds: Int32(smartSync["batchIntervalSeconds"] as? Int ?? 60)
      ),
      wakeLock: args["wakeLock"] as! Bool,
      buffer: args["buffer"] as! Bool,
      preferPlatformProviders: args["preferPlatformProviders"] as! Bool,
      notification: NotificationConfig(text: notification["text"] as! String)
    )
  }

  private func parseAccuracy(_ name: String) -> Accuracy {
    switch name {
    case "HIGHEST": return Accuracy.highest
    case "HIGH": return Accuracy.high
    case "LOW": return Accuracy.low
    default: return Accuracy.medium
    }
  }

  private func parseSyncMode(_ name: String) -> SyncMode {
    switch name {
    case "BATCH": return SyncMode.batch
    case "OFFLINE": return SyncMode.offline
    default: return SyncMode.instant
    }
  }
}
