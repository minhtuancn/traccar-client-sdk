# Traccar Client SDK

A Kotlin Multiplatform background location tracking SDK for [Traccar](https://www.traccar.org) - and any other server that accepts the same simple HTTP protocol. Runs on Android and iOS, with Flutter and React Native wrappers.

- **Native** - Maven Central (`org.traccar:traccar-client-sdk`), iOS via Swift Package Manager
- **Flutter** - pub.dev (`traccar_client_sdk`)
- **React Native** - npm (`react-native-traccar-client-sdk`)

## Fork enhancements

This fork adds reliability features intended for long-running managed-device tracking while retaining the original Traccar pipeline and protocol compatibility:

- fresh-position heartbeat age filtering
- adaptive Driving / Walking / Stationary / Charging / Battery Saver profiles
- runtime location subscription changes without adding a second GPS service
- Smart Sync with Instant / Batch / Offline durable-queue delivery policies
- manual `syncNow()` queue draining
- PR verification via GitHub CI and Woodpecker CI

See:

- [Adaptive Tracking Profiles](docs/ADAPTIVE_TRACKING.md)
- [Smart Offline and Batch Sync](docs/SMART_SYNC.md)

## Documentation

Upstream documentation - installation, configuration, API, and architecture - is on the Traccar website:

- **Overview:** https://www.traccar.org/traccar-client-sdk/
- **Flutter:** https://www.traccar.org/traccar-client-sdk-flutter/
- **React Native:** https://www.traccar.org/traccar-client-sdk-react-native/

## License

Apache License 2.0. See [LICENSE](LICENSE). Third-party projects such as Colota are used only as architecture/behavior references; their source is not copied into this fork.
