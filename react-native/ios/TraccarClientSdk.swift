import Foundation
import React
import TraccarClientSDK

enum TrackerError: Error {
  case notInitialized
}

@objc(TraccarClientSdk)
class TraccarClientSdk: NSObject {

  @objc static func requiresMainQueueSetup() -> Bool {
    return false
  }

  @objc(initTracker:resolver:rejecter:)
  func initTracker(
    _ config: NSDictionary,
    resolver resolve: @escaping RCTPromiseResolveBlock,
    rejecter reject: @escaping RCTPromiseRejectBlock
  ) {
    let parsed = parseConfig(config)
    run(resolve, reject) {
      _ = try await TrackerKt.sharedTracker(config: parsed)
      return nil
    }
  }

  @objc(setConfig:resolver:rejecter:)
  func setConfig(
    _ config: NSDictionary,
    resolver resolve: @escaping RCTPromiseResolveBlock,
    rejecter reject: @escaping RCTPromiseRejectBlock
  ) {
    let parsed = parseConfig(config)
    run(resolve, reject) {
      guard let tracker = try await TrackerKt.sharedTracker() else {
        throw TrackerError.notInitialized
      }
      _ = try await tracker.updateConfig(newConfig: parsed)
      return nil
    }
  }

  @objc(start:rejecter:)
  func start(
    _ resolve: @escaping RCTPromiseResolveBlock,
    rejecter reject: @escaping RCTPromiseRejectBlock
  ) {
    run(resolve, reject) {
      guard let tracker = try await TrackerKt.sharedTracker() else {
        throw TrackerError.notInitialized
      }
      try await tracker.start()
      return nil
    }
  }

  @objc(stop:rejecter:)
  func stop(
    _ resolve: @escaping RCTPromiseResolveBlock,
    rejecter reject: @escaping RCTPromiseRejectBlock
  ) {
    run(resolve, reject) {
      try await TrackerKt.sharedTracker()?.stop()
      return nil
    }
  }

  @objc(requestPosition:resolver:rejecter:)
  func requestPosition(
    _ alarm: NSString?,
    resolver resolve: @escaping RCTPromiseResolveBlock,
    rejecter reject: @escaping RCTPromiseRejectBlock
  ) {
    run(resolve, reject) {
      let uploaded = try await TrackerKt.sharedTracker()?.requestPosition(alarm: alarm as String?)
      return uploaded?.boolValue ?? false
    }
  }

  @objc(isTracking:rejecter:)
  func isTracking(
    _ resolve: @escaping RCTPromiseResolveBlock,
    rejecter reject: @escaping RCTPromiseRejectBlock
  ) {
    run(resolve, reject) {
      guard let tracker = try await TrackerKt.sharedTracker() else { return false }
      return (tracker.state.value as? State)?.enabled ?? false
    }
  }

  @objc(getLogs:rejecter:)
  func getLogs(
    _ resolve: @escaping RCTPromiseResolveBlock,
    rejecter reject: @escaping RCTPromiseRejectBlock
  ) {
    run(resolve, reject) {
      guard let tracker = try await TrackerKt.sharedTracker() else {
        return [[String: Any]]()
      }
      return try await tracker.getLogs().map {
        ["time": $0.time, "message": $0.message] as [String: Any]
      }
    }
  }

  @objc(clearLogs:rejecter:)
  func clearLogs(
    _ resolve: @escaping RCTPromiseResolveBlock,
    rejecter reject: @escaping RCTPromiseRejectBlock
  ) {
    run(resolve, reject) {
      try await TrackerKt.sharedTracker()?.clearLogs()
      return nil
    }
  }

  private func run(
    _ resolve: @escaping RCTPromiseResolveBlock,
    _ reject: @escaping RCTPromiseRejectBlock,
    block: @escaping () async throws -> Any?
  ) {
    Task {
      do {
        resolve(try await block())
      } catch {
        reject(String(describing: type(of: error)), error.localizedDescription, error)
      }
    }
  }

  private func parseConfig(_ args: NSDictionary) -> Config {
    let location = args["location"] as! [String: Any]
    let adaptive = args["adaptiveTracking"] as? [String: Any] ?? [:]
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
}
