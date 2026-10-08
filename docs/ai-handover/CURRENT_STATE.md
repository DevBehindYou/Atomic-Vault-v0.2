# Current State

Last updated: 2026-10-08
Verified against commit: `b28797d`
Confidence: HIGH

## Git

| Branch | Commit | Role |
|---|---|---|
| `claude/stoic-lamport-t601ql` | `b28797d` | Work branch; all PRs merge here |
| `main` | `2217d9a` | Release branch; untouched; push = signed release |
| `claude/stoic-lamport-t601ql-phone` | PR #19 | Phone-check script + this handover |
| other `claude/stoic-lamport-t601ql-*` | merged | Safe to delete (the bot could not delete them; 403) |

## Build and CI

- CI is GitHub Actions only (`.github/workflows/android-ci.yml`). There is no
  local Android build in this project's workflow, and in the cloud
  environment Google Maven is blocked.
- Jobs: verify (design check, unused imports, unit tests, lint, Roborazzi
  snapshots, minified build), emulator matrix (debug and minified, API 29),
  fingerprint check (API 29), screen-lock check (API 30), upgrade check (PRs
  only: the base build creates a vault, then the new build must unlock it),
  instrumented tests.
- Latest green run with all of the work branch's code: #151 (PR #25;
  `AtomicVault-UI-Snapshots-151`, `AtomicVault-Debug-APK-151`, and the other
  artifacts). The upgrade check on release PR #20 runs from the real 0.2.1
  build on `main`.

## Features (all VERIFIED by CI only, not on a real phone)

| Feature | PR | Key files |
|---|---|---|
| Lock after no taps (Off/1/5/15, default 5) | #12 | `security/VaultLifecycleObserver.kt`, `security/IdleLockStore.kt` |
| CSV import (Chrome, Bitwarden, 1Password, KeePass, Firefox) | #13 | `backup/CsvImport.kt`, `ui/backup/CsvImportPane.kt` |
| Password history (10 per login) | #14 | `database/VaultRepositoryImpl.kt` (`password_history` table) |
| Screen-lock unlock (API 30+, no fingerprint) | #15 | `keystore/BiometricGatedKeyStore.kt`, `security/AppBiometricManager.kt` |
| Offline breach check (1M passwords, Bloom filter) | #16 | `security/BreachedPasswords.kt`, `assets/breached_passwords.bloom`, `tools/breach_filter/` |
| Sealed vault envelope (replaces security-crypto) | #8 | `keystore/EnvelopeSealer.kt`, `keystore/VaultMetaStore.kt` |
| SQLCipher artifact swap | #9 | `database/VaultDatabase.kt` |
| Toolchain / SDK 36, API-30 type isolation | #10 | `autofill/InlineApi30.kt` |
| Native Argon2id (argon2kt; fixes vault-creation OOM, ISS-001) | #21 | `crypto/Argon2Kdf.kt`, `Argon2KdfVectorTest`, `Argon2KdfDeviceTest` |
| Version 0.4.0 / versionCode 3 | #23, #24 | `app/build.gradle.kts` |

Sources are under `app/src/main/java/com/example/` (package not renamed yet).

## Unknown

- Behavior on a real phone: fingerprint, Gboard inline chips in other apps,
  layout at large fonts, how the screens look.

## Open Blockers (for the cloud agent; not for a local agent)

- The cloud session cannot reach a USB phone, and CI artifact downloads
  (`*.blob.core.windows.net`) were blocked by its network policy.
