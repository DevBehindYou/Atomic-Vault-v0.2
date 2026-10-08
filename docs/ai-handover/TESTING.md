# Testing

Last verified commit: `ab7c515` (work branch), PR #26 run 37751364424 and release PR #20 (all 7 jobs green).

| Check | Status | Evidence |
|---|---|---|
| Design-token check, unused imports | VERIFIED | CI verify job |
| Unit + Robolectric tests | VERIFIED | CI; new this session: `VaultLifecycleObserverTest` (idle), `CsvImportTest`, `BreachedPasswordsTest`, `DdlTest` |
| Lint, minified `assembleInternal` | VERIFIED | CI |
| Emulator debug + minified (API 29) | VERIFIED | create vault, all tabs, restart, unlock, Autofill locked response |
| Fingerprint (API 29) | VERIFIED | enrol, arm, wrong finger rejected, unlock, cancel |
| Screen-lock (API 30, PIN only) | VERIFIED | arm with PIN, unlock after restart, cancel, then password |
| Upgrade (base build vault, then new build unlocks) | VERIFIED | each code PR |
| Instrumented (real SQLCipher/Keystore) | VERIFIED | 15 tests: `VaultMetaStoreDeviceTest`, `PasswordHistoryDeviceTest`, `Argon2KdfDeviceTest` (native, 64 MiB known answer), `BackupRestoreDeviceTest` (restore under a new key, replace not merge, wrong passphrase writes nothing) |
| Upgrade from released 0.2.1 (`main`) | VERIFIED | release PR #20: 0.2.1 vault unlocks in 0.4.0, keyboard notice shown once |
| Breach filter false-positive rate | VERIFIED | 0.09% measured (200k random strings); test asserts < 0.3% |

## Not Run

- Any real phone (TASK-001, TASK-002)
- Human review of the Roborazzi snapshots (TASK-003)
- Gboard inline suggestions in third-party apps; large-font layout
