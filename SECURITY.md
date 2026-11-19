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

## Data at rest and SQL

- **Ledger and tickets** live in a Room database on SQLCipher. The key is 32 random bytes,
  stored only in an `EncryptedFile` (AES-256-GCM) under an Android Keystore master key.
- **Settings, budgets, goals and auth flags** live in `EncryptedSharedPreferences`.
- **SQL** goes through Room `@Query` with bound parameters. The only raw `execSQL` in app code
  is fixed DDL in `AppDatabaseMigrations`; the SMS inbox query uses `LIKE ?` with arguments.
  No user or SMS text is ever concatenated into SQL.
- **WorkManager's database** is not encrypted; pass reminders store only a ticket id and kind.

MobSF's APK scan reports `android_sql_raw_query` ("App uses SQLite Database and execute raw
SQL query"). As of 2026-11-19 every flagged class is library code (SQLCipher, Firebase
Analytics and Crashlytics, Google Data Transport, ML Kit, AndroidX), none of it LipaBill's.
Map R8 names back with `mapping.txt` from the same build before treating it as new.

## Supported versions

Only the latest release of each flavor gets security fixes.
