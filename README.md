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
./gradlew :app:assembleInternalRelease  # Sideload + self-hosted OTA
./scripts/publish-ota.sh               # Build APK + write ota/dist/latest.json
```

Flavors: **`play`** (default, Play submission — updates via Play in-app updates) and **`internal`** (sideload + **self-hosted OTA** from `https://lipabill.app/ota/latest.json`). Both keep SMS ledger + USSD Accessibility fill.

### OTA (skip the Play review queue)

Play review cannot be bypassed for Play installs. For people on the **internal** APK:

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`
2. Run `./scripts/publish-ota.sh`
   (or run the **Release (internal OTA)** workflow, which builds the same files with provenance)
3. Upload `ota/dist/LipaBill-…-internal.apk`, `ota/dist/latest.json` and `ota/dist/latest.json.sig` to the lipabill.app Vercel project under `/ota/`
4. Open LipaBill → Settings → **Check for updates** (or relaunch — auto-check once per process)

Manifest shape: see `ota/latest.example.json`. The app checks `latest.json.sig` (ECDSA P-256,
key `keystore/ota-manifest-signing.pem`) before reading the manifest, then the APK SHA-256 and
signing certificate before install.

### Security scans

- **mobsfscan** runs on every PR/push over the public sources (results in the Security tab).
- `./scripts/mobsf-scan.sh [apk]` runs full MobSF in Docker against a release APK locally;
  reports stay in `mobsf-reports/` because they cover the private modules.

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
