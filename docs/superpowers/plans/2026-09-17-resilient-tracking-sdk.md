# Resilient Tracking SDK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the Traccar Client SDK with fresh stationary heartbeats, adaptive tracking profiles, and smart durable-queue synchronization without breaking the OsmAnd-compatible upload path.

**Architecture:** Keep location acquisition, queueing, and upload in the Kotlin Multiplatform SDK. Heartbeat freshness and sync policy live in common code; platform components only provide motion/network signals. Preserve the existing SQLDelight queue and `HttpUploader` rather than creating duplicate storage or an incompatible batch protocol.

**Tech Stack:** Kotlin Multiplatform, kotlinx.coroutines, kotlinx.serialization, SQLDelight, Koin, Android Fused/LocationManager, Core Location, Flutter and React Native bridges.

**Spec:** `docs/superpowers/plans/2026-09-17-resilient-tracking-sdk.md`

## Global Constraints

- Preserve Apache-2.0 licensing; Colota is architecture/behavior reference only and no AGPL source is copied.
- Preserve existing `Config` deserialization through defaults for older persisted configurations.
- Preserve the OsmAnd form upload contract; "batch" means policy-controlled queue draining, not a new JSON-array endpoint.
- `heartbeatIntervalSeconds = 0` continues to disable heartbeat scheduling.
- A user-requested tracker `stop()` must never be undone by recovery/profile/sync logic.
- Android and iOS share common policy semantics even if signal sources differ.

---

### Task 1: Fresh-position heartbeat policy

**Files:**
- Create: `core/src/commonMain/kotlin/org/traccar/client/HeartbeatPolicy.kt`
- Create: `core/src/commonTest/kotlin/org/traccar/client/HeartbeatPolicyTest.kt`
- Modify: `core/src/commonMain/kotlin/org/traccar/client/Config.kt`
- Modify: `core/src/commonMain/kotlin/org/traccar/client/TrackerEngine.kt`
- Modify: `core/src/commonMain/kotlin/org/traccar/client/CoreModule.kt`
- Modify Flutter and React Native config bridges.

**Interfaces:**
- `LocationConfig.heartbeatMaxAgeSeconds: Int`
- `resolveHeartbeatPosition(candidate: Position?, nowMillis: Long, maxAgeSeconds: Int): Position`

- [ ] Write tests for fresh, stale, missing, and freshness-disabled candidates.
- [ ] Implement the pure heartbeat policy.
- [ ] Apply the policy in `TrackerEngine` after `fetchOnce()`.
- [ ] Expose the field through Flutter/React Native bridges.
- [ ] Run common tests and bridge builds.

### Task 2: Adaptive tracking profiles

**Files:**
- Create: `core/src/commonMain/kotlin/org/traccar/client/TrackingProfile.kt`
- Create: `core/src/commonMain/kotlin/org/traccar/client/TrackingProfileEngine.kt`
- Create: `core/src/commonTest/kotlin/org/traccar/client/TrackingProfileEngineTest.kt`
- Modify: `Signal.kt`, `State.kt`, platform motion detectors and location sources.

**Interfaces:**
- `TrackingProfileName`: `DRIVING`, `WALKING`, `STATIONARY`, `CHARGING`, `BATTERY_SAVER`, `DEFAULT`
- `TrackingProfileConfig`: location cadence overrides plus activation/deactivation thresholds.
- `MotionSignal`: common motion classification emitted by platform detectors.

- [ ] Add deterministic profile-selection tests including debounce/hysteresis.
- [ ] Add common profile model and engine.
- [ ] Emit richer motion signals from Android/iOS without removing stationary signals.
- [ ] Make location sources react to the selected effective profile configuration.
- [ ] Expose profile configuration and current profile through bridges/status APIs.

### Task 3: Smart offline / periodic queue sync

**Files:**
- Create: `core/src/commonMain/kotlin/org/traccar/client/SyncPolicy.kt`
- Create: `core/src/commonTest/kotlin/org/traccar/client/SyncPolicyTest.kt`
- Modify: `Config.kt`, `TrackerEngine.kt`, `PositionQueue.kt`, `DatabaseQueue.kt` as needed.
- Modify Flutter/React Native bridges.

**Interfaces:**
- `SyncMode`: `IMMEDIATE`, `PERIODIC`, `OFFLINE_ONLY`
- `SyncConfig(mode, intervalSeconds, maxItemsPerPass)`

- [ ] Add policy tests for immediate, periodic, offline-only and network restoration behavior.
- [ ] Keep all incoming positions in the existing durable SQLDelight queue when buffering is enabled.
- [ ] Gate queue draining by sync mode and interval.
- [ ] Limit each periodic drain pass without changing the one-position-per-request OsmAnd protocol.
- [ ] Preserve exponential retry/backoff on transport failures.
- [ ] Expose sync configuration and queue status through bridges.

### Task 4: Integration and compatibility

- [ ] Update README/changelog with fork behavior and migration defaults.
- [ ] Build Android core + Flutter plugin.
- [ ] Build/check iOS bridge signatures.
- [ ] Run common tests.
- [ ] Open a draft PR and keep it draft until CI is green.
