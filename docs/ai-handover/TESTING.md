# Testing

Last verified commit: `b28797d` (work branch), PR runs for #24 and #25 (all 7 jobs green).

| Check | Status | Evidence |
|---|---|---|
| Design-token check, unused imports | VERIFIED | CI verify job |
| Unit + Robolectric tests | VERIFIED | CI; new this session: `VaultLifecycleObserverTest` (idle), `CsvImportTest`, `BreachedPasswordsTest`, `DdlTest` |
| Lint, minified `assembleInternal` | VERIFIED | CI |
| Emulator debug + minified (API 29) | VERIFIED | create vault, all tabs, restart, unlock, Autofill locked response |
| Fingerprint (API 29) | VERIFIED | enrol, arm, wrong finger rejected, unlock, cancel |
| Screen-lock (API 30, PIN only) | VERIFIED | arm with PIN, unlock after restart, cancel, then password |
| Upgrade (base build vault, then new build unlocks) | VERIFIED | each code PR |
| Instrumented (real SQLCipher/Keystore) | VERIFIED | `VaultMetaStoreDeviceTest`, `PasswordHistoryDeviceTest` |
| Breach filter false-positive rate | VERIFIED | 0.09% measured (200k random strings); test asserts < 0.3% |

## Not Run

- Any real phone (TASK-001, TASK-002)
- Human review of the Roborazzi snapshots (TASK-003)
- Gboard inline suggestions in third-party apps; large-font layout
