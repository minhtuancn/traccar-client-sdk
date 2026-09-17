import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-traccar-client-sdk' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n';

const TraccarClientSdk = NativeModules.TraccarClientSdk
  ? NativeModules.TraccarClientSdk
  : new Proxy({}, { get() { throw new Error(LINKING_ERROR); } });

export type Accuracy = 'HIGHEST' | 'HIGH' | 'MEDIUM' | 'LOW';
export type SyncMode = 'INSTANT' | 'BATCH' | 'OFFLINE';

export interface LocationConfig {
  accuracy?: Accuracy;
  distanceMeters?: number;
  intervalSeconds?: number;
  angleDegrees?: number;
  stopDetection?: boolean;
  stopTimeoutSeconds?: number;
  stationaryRadiusMeters?: number;
  heartbeatIntervalSeconds?: number;
  heartbeatMaxAgeSeconds?: number;
}

export interface AdaptiveTrackingConfig {
  enabled?: boolean;
  transitionDelaySeconds?: number;
  lowBatteryThresholdPercent?: number;
  drivingEnterSpeedMps?: number;
  drivingExitSpeedMps?: number;
  drivingDistanceMeters?: number;
  walkingDistanceMeters?: number;
  chargingIntervalSeconds?: number;
  batterySaverDistanceMeters?: number;
}

export interface SmartSyncConfig {
  enabled?: boolean;
  mode?: SyncMode;
  batchSize?: number;
  batchIntervalSeconds?: number;
}

export interface NotificationConfig { text?: string; }

export interface Config {
  serverUrl: string;
  deviceId: string;
  location?: LocationConfig;
  adaptiveTracking?: AdaptiveTrackingConfig;
  smartSync?: SmartSyncConfig;
  wakeLock?: boolean;
  buffer?: boolean;
  preferPlatformProviders?: boolean;
  notification?: NotificationConfig;
}

export interface LogEntry { time: number; message: string; }

function normalizeConfig(config: Config): Required<Config> {
  const location = config.location ?? {};
  const adaptive = config.adaptiveTracking ?? {};
  const smartSync = config.smartSync ?? {};
  const notification = config.notification ?? {};
  return {
    serverUrl: config.serverUrl,
    deviceId: config.deviceId,
    location: {
      accuracy: location.accuracy ?? 'MEDIUM',
      distanceMeters: location.distanceMeters ?? 75,
      intervalSeconds: location.intervalSeconds ?? 300,
      angleDegrees: location.angleDegrees ?? 0,
      stopDetection: location.stopDetection ?? true,
      stopTimeoutSeconds: location.stopTimeoutSeconds ?? 60,
      stationaryRadiusMeters: location.stationaryRadiusMeters ?? 100,
      heartbeatIntervalSeconds: location.heartbeatIntervalSeconds ?? 0,
      heartbeatMaxAgeSeconds: location.heartbeatMaxAgeSeconds ?? 300,
    },
    adaptiveTracking: {
      enabled: adaptive.enabled ?? false,
      transitionDelaySeconds: adaptive.transitionDelaySeconds ?? 10,
      lowBatteryThresholdPercent: adaptive.lowBatteryThresholdPercent ?? 20,
      drivingEnterSpeedMps: adaptive.drivingEnterSpeedMps ?? 4.2,
      drivingExitSpeedMps: adaptive.drivingExitSpeedMps ?? 2.0,
      drivingDistanceMeters: adaptive.drivingDistanceMeters ?? 10,
      walkingDistanceMeters: adaptive.walkingDistanceMeters ?? 20,
      chargingIntervalSeconds: adaptive.chargingIntervalSeconds ?? 10,
      batterySaverDistanceMeters: adaptive.batterySaverDistanceMeters ?? 100,
    },
    smartSync: {
      enabled: smartSync.enabled ?? false,
      mode: smartSync.mode ?? 'INSTANT',
      batchSize: smartSync.batchSize ?? 25,
      batchIntervalSeconds: smartSync.batchIntervalSeconds ?? 60,
    },
    wakeLock: config.wakeLock ?? false,
    buffer: config.buffer ?? true,
    preferPlatformProviders: config.preferPlatformProviders ?? false,
    notification: { text: notification.text ?? 'Location tracking' },
  };
}

export function init(config: Config): Promise<void> {
  return TraccarClientSdk.initTracker(normalizeConfig(config));
}
export function setConfig(config: Config): Promise<void> {
  return TraccarClientSdk.setConfig(normalizeConfig(config));
}
export function start(): Promise<void> { return TraccarClientSdk.start(); }
export function stop(): Promise<void> { return TraccarClientSdk.stop(); }
export function syncNow(): Promise<void> { return TraccarClientSdk.syncNow(); }
export function requestPosition(alarm?: string): Promise<boolean> {
  return TraccarClientSdk.requestPosition(alarm ?? null);
}
export function isTracking(): Promise<boolean> { return TraccarClientSdk.isTracking(); }
export function getLogs(): Promise<LogEntry[]> { return TraccarClientSdk.getLogs(); }
export function clearLogs(): Promise<void> { return TraccarClientSdk.clearLogs(); }
