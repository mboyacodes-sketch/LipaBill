# LipaBill

Personal Android wallet companion for tracking mobile-money activity and related
receipts on-device.

Built with Kotlin, Jetpack Compose, and local encrypted storage. Access is gated
behind the device lock (biometric or PIN).

Some implementation modules are kept private and are not part of this repository.
A full local workspace is required to build a production binary.

## Requirements

- JDK 17
- Android SDK (minSdk 26)

```bash
./gradlew assembleDebug
```

## Notes

- Repo name and Play listing name do not need to match.
- Do not commit signing material or local SDK paths (`keystore/`, `local.properties`).
