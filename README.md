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

### Pay-link web fallback

Shared pay QRs use `https://lipabill.app/p/{token}`. Without LipaBill installed, that URL
serves the static landing in [`web/`](web/README.md) (copy details / open M-Pesa / Get LipaBill).
Deploy `web/` to the same Vercel host (include `/.well-known/assetlinks.json` for App Links).

## Maestro suites

Two entry points. Phone must be unlocked (fingerprint or swipe). Neither suite
types an M-Pesa PIN; both stop if a PIN pad appears (`guard-pin.yaml`).

```bash
./promo/run.sh   # Demo: SMS denied, demo ledger, debug Send walkthrough, stills
./promo/export.sh
./live/run.sh    # Live: grant SMS/phone/camera/alerts + Accessibility, real inbox
```

| | Demo (`./promo/run.sh`) | Live (`./live/run.sh`) |
|---|---|---|
| Flow | `.maestro/promo.yaml` | `.maestro/live/happy-path.yaml` |
| SMS | Denied (`Continue without SMS`) | Allowed (adb + Allow dialogs) |
| Ledger | Debug `DemoLedger` seed | Real confirmation SMS |
| Accessibility | Skipped | Enabled via adb (+ UI fallback) |
| Send / Pay | Debug walkthrough stills only | Send `0711222333` & Pay Till `111222` to PIN, then Cancel |

App ID is `com.lipabill.app`.

## Notes

- Repo name and Play listing name do not need to match.
- Do not commit signing material or local SDK paths (`keystore/`, `local.properties`).
