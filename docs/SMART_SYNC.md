# Smart Offline and Batch Sync

This fork extends the existing SQLDelight-backed durable position queue with a configurable drain policy. It does **not** introduce a second database, uploader, or wire protocol.

## Modes

### Instant

`INSTANT` preserves the existing Traccar Client SDK behavior. Buffered positions are uploaded as soon as connectivity and the server permit it. Each automatic drain step processes one queue item, and the loop continues while items remain.

### Batch

`BATCH` keeps positions in the existing durable queue and drains them in bursts. The defaults are:

- `batchSize = 25`
- `batchIntervalSeconds = 60`

A batch is a scheduling/draining concept only. Each position is still sent through the normal Traccar/OsmAnd-compatible uploader as an individual HTTP request. This preserves server compatibility and avoids inventing a fork-specific multi-position payload.

### Offline

`OFFLINE` disables automatic queue draining. Positions continue to be recorded in the durable SQLDelight queue while tracking is enabled. Call `syncNow()` to force a drain when the operator or managed-device policy decides to upload.

## Configuration

```kotlin
val config = Config(
    serverUrl = "https://example.com",
    deviceId = "device-1",
    smartSync = SmartSyncConfig(
        enabled = true,
        mode = SyncMode.BATCH,
        batchSize = 25,
        batchIntervalSeconds = 60,
    ),
)
```

Flutter:

```dart
final config = Config(
  serverUrl: 'https://example.com',
  deviceId: 'device-1',
  smartSync: const SmartSyncConfig(
    enabled: true,
    mode: SyncMode.batch,
    batchSize: 25,
    batchIntervalSeconds: 60,
  ),
);

await tracker.syncNow();
```

React Native exposes the same `smartSync` fields and `syncNow()` function.

## Retry and connectivity

Smart Sync reuses the existing uploader and exponential retry policy. A queued position is removed only after a successful upload. If the network is offline, an attempted drain waits for connectivity to return. Upload failures retain the queue head and back off from the existing initial delay up to the existing maximum delay.

Manual sync requests use a full drain limit. Batch automatic drains use `batchSize`. Offline mode waits without busy-looping until `syncNow()` is requested.

## Backward compatibility

`SmartSyncConfig.enabled` defaults to `false`. With Smart Sync disabled, the queue behaves like the upstream SDK: automatic immediate draining remains active. This lets existing applications consume the fork without changing sync behavior until they explicitly enable a mode.

## Interaction with adaptive profiles

Tracking profiles control location acquisition; Smart Sync controls queue delivery. They are intentionally independent:

```text
Motion / battery / charging
          |
          v
Adaptive Tracking Profile
          |
          v
Location pipeline
          |
          v
SQLDelight durable queue
          |
          v
Smart Sync policy
  Instant / Batch / Offline
          |
          v
Existing Traccar uploader
```

## License boundary

Colota is used only as an architecture and behavior reference. No Colota source code is copied into this Apache-2.0 fork.
