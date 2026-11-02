# LipaBill

See where your money went, then send or pay — on this Android, encrypted
on-device. Tickets are what the money already became.

Built with Kotlin, Jetpack Compose, and local encrypted storage. Access is gated
behind the device lock (biometric or PIN).

Some implementation modules are kept private and are not part of this repository.
A full local workspace is required to build a production binary.

## Requirements

- JDK 17
- Android SDK (minSdk 26)

```bash
./gradlew :app:assemblePlayDebug
./gradlew :app:bundlePlayRelease      # Play Store AAB
./gradlew :app:assembleInternalRelease  # Firebase / sideload
```

Flavors: **`play`** (default, Play submission) and **`internal`** (sideload helpers such as Settings restricted-unlock auto-navigation). Both keep SMS ledger + USSD Accessibility fill.

## Notes

- Repo name and Play listing name do not need to match.
- Do not commit signing material or local SDK paths (`keystore/`, `local.properties`).
