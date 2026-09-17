# Upstream Sync Procedure

This fork should stay easy to compare with and rebase onto the official `traccar/traccar-client-sdk` repository. Enhanced tracking changes are intentionally kept in small stacked branches so upstream changes can be reconciled feature by feature.

## Remotes

Use the fork as `origin` and the official repository as `upstream`:

```shell
git remote -v
git remote add upstream https://github.com/traccar/traccar-client-sdk.git
git fetch --all --prune
```

If `upstream` already exists, do not add it again.

## Never rebase production blindly

Before syncing:

1. Confirm all fork PR heads have green CI.
2. Record the current client gitlink that pins this SDK.
3. Create a temporary integration branch from the newest fork feature head.
4. Fetch the latest official `upstream/main`.
5. Rebase or merge only in the temporary branch first.
6. Run the full SDK verification before moving any reviewed feature branch.

Recommended temporary branch:

```shell
git switch feature/sync-status-telemetry
git switch -c integration/upstream-sync-YYYYMMDD
git fetch upstream
git rebase upstream/main
```

A merge-based integration branch is acceptable when preserving upstream merge history is preferred, but do not mix merge and rebase strategies inside one review without documenting why.

## Conflict policy

Resolve conflicts according to ownership, not by choosing "ours" or "theirs" globally.

### Prefer upstream for unchanged platform plumbing

If the fork did not intentionally change an area, prefer the official implementation. Typical examples include dependency upgrades, permissions, build tooling, packaging and unrelated platform fixes.

### Reapply fork behavior explicitly

For conflicts touching these fork-owned behaviors, preserve the feature contract while adapting to the new upstream architecture:

- `heartbeatMaxAgeSeconds` and stale-heartbeat handling
- adaptive tracking configuration/profile policy
- Smart Sync modes and manual `syncNow()` behavior
- queue telemetry (`count`, `lastSuccessfulSyncMillis`)

Do not copy old files wholesale over newer upstream files. Port the smallest behavioral delta into the updated structure.

### Durable queue invariants

After any conflict resolution, verify all of the following:

- there is still one SQLDelight position queue
- FIFO ordering is preserved
- rows are removed only after successful upload
- Offline mode never auto-drains
- manual sync drains explicitly
- retry/backoff remains bounded
- queue telemetry reads the same durable queue used for delivery

### State compatibility

Persisted `State` changes must remain backward compatible. New serialized fields should have defaults or be nullable unless an explicit migration is provided and tested.

## Verification after upstream sync

Run:

```shell
./gradlew :core:allTests --no-configuration-cache
cd flutter
flutter pub get
flutter analyze
```

Also inspect the SDK PR diff against the new upstream base. A green compile alone is not enough if the fork delta unexpectedly expands.

For changes to Android/iOS location providers, heartbeat scheduling, queueing or background lifecycle, repeat the relevant real-device validation from the client resilient-tracking plan.

## Updating the client pin

Only after the SDK integration branch is reviewed and green:

1. choose the exact SDK commit to consume
2. update `vendor/traccar-client-sdk` in `minhtuancn/traccar-client`
3. commit the gitlink change
4. run client Flutter analysis/tests and Android APK build
5. verify the client PR records the exact pinned SDK SHA

Never point production at a moving SDK branch.

## Suggested cadence

Sync when one of these occurs:

- upstream releases a relevant SDK version
- upstream changes Android/iOS background-location behavior
- upstream changes queue/uploader/config persistence code
- a security or dependency update affects the fork
- before a production release after a long period without upstream reconciliation

Avoid routine rebases in the middle of device reliability testing unless the upstream change is needed; changing the base invalidates part of the evidence being collected.
