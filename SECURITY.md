# Security policy

LipaBill reads M-Pesa confirmation SMS, helps fill USSD payment menus, and keeps an
encrypted ledger on the device. Security reports are welcome.

## Reporting a vulnerability

Please report privately through GitHub:
**Security → Report a vulnerability** on this repository
(<https://github.com/mboyacodes-sketch/LipaBill/security/advisories/new>).

Do not open a public issue, pull request, or discussion for a security problem.

Include what you can of:

- the affected app version and flavor (Play or internal APK)
- steps to reproduce, and what an attacker gains
- device model and Android version

You should get an acknowledgement within 3 working days. Please allow time for a fix
to reach users through Play review before disclosing publicly.

## Scope

In scope:

- The Android app (`com.lipabill.app`), both `play` and `internal` builds
- The internal OTA channel: `https://lipabill.app/ota/latest.json`, its `.sig`, and APKs under `/ota/`
- Pay links (`https://lipabill.app/p/…`, `lipabill://p/…`) and App Links verification

Especially interesting:

- Anything that moves money, changes a payment recipient or amount, or touches the M-Pesa PIN
- Reading the ledger, exports, or SMS content from another app or without device unlock
- Installing an update not signed by LipaBill, or bypassing the manifest signature

Out of scope:

- Attacks that need a rooted device, an unlocked bootloader, or a user-installed CA
- Spoofed SMS that only add an entry to the ledger without leading to a payment
- Missing hardening headers on static pages that hold no user data
- Findings from automated scanners without a demonstrated impact

## Supported versions

Only the latest release of each flavor gets security fixes.
