# Traccar Client SDK

A Kotlin Multiplatform background location tracking SDK for [Traccar](https://www.traccar.org) - and any other server that accepts the same simple HTTP protocol. Runs on Android and iOS, with Flutter and React Native wrappers.

- **Native** - Maven Central (`org.traccar:traccar-client-sdk`), iOS via Swift Package Manager
- **Flutter** - pub.dev (`traccar_client_sdk`)
- **React Native** - npm (`react-native-traccar-client-sdk`)

## Fork status

The resilient-tracking enhancement stack is merged to this fork's `main` branch. It is consumed by the Android beta `minhtuancn/traccar-client` `10.2.0-beta.1+159` through an immutable git submodule pin and Gradle composite-build dependency substitution.

## Fork enhancements

This fork adds reliability features intended for long-running managed-device tracking while retaining the original Traccar pipeline and protocol compatibility:

- fresh-position heartbeat age filtering
- adaptive Driving / Walking / Stationary / Charging / Battery Saver profiles
- runtime location subscription changes without adding a second GPS service
- Smart Sync with Instant / Batch / Offline durable-queue delivery policies
- manual `syncNow()` queue draining
- durable queue telemetry: pending-position count and persisted last-successful-sync timestamp
- PR verification via GitHub CI and Woodpecker CI

See:

- [Adaptive Tracking Profiles](docs/ADAPTIVE_TRACKING.md)
- [Smart Offline and Batch Sync](docs/SMART_SYNC.md)
- [Upstream Sync Procedure](docs/UPSTREAM_SYNC.md)

## Fork maintenance

The fork is consumed by `minhtuancn/traccar-client` through an immutable git submodule pin and Android Gradle composite-build substitution. Do not publish fork builds over Traccar's official Maven coordinates. Use the upstream sync procedure to reconcile official changes in a temporary integration branch, run the full SDK verification, then update the client to an exact reviewed SDK commit.

The SDK keeps the original SQLDelight durable queue and Traccar/OsmAnd-compatible per-position protocol. Smart Sync controls queue draining policy; it does not add a second queue or a proprietary batch wire format.

## Verification

The merged enhancement stack has passed Kotlin Multiplatform core verification and Flutter wrapper analysis. The integrated Android client stack has also passed Flutter analysis/tests and a full debug APK build using the pinned SDK source.

Physical Android validation remains important for Doze/OEM background behavior, reboot recovery, motion profile transitions, and prolonged offline/reconnect scenarios.

## Documentation

Upstream documentation - installation, configuration, API, and architecture - is on the Traccar website:

- **Overview:** https://www.traccar.org/traccar-client-sdk/
- **Flutter:** https://www.traccar.org/traccar-client-sdk-flutter/
- **React Native:** https://www.traccar.org/traccar-client-sdk-react-native/

## License

Apache License 2.0. See [LICENSE](LICENSE). Third-party projects such as Colota are used only as architecture/behavior references; their source is not copied into this fork.
