# Tasks left

> **Update 2026-10-04:** most agent-ready items below were done on branch
> `claude/atomic-vault-analysis-1fj2ok`; see [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md)
> (progress table) and [CHANGELOG.md](CHANGELOG.md). Done: T1 (TOTP wipe), T6
> (backup round-trip, unit level), T8 (batched export), T4 is obsolete (the
> keyboard was removed). Still open: T2, T3, T5, T7, T9-T12, and the phone test
> (U1) of everything since.
>
> **Update 2026-10-05:** T5 and T12 done on `claude/stoic-lamport-t601ql`
> (CI #52). T7, T2 and T9 are open as pull requests into that branch
> (#2, #3, #4). T3 still needs the user's decision (U4); T10 and T11 are
> unchanged.
>
> **Update 2026-10-06:** T2 (#3), T7 (#2), T9 (#4, plus dependencies #5 and
> the toolchain/SDK 36 upgrade #10) and T10 (#7) are merged into
> `claude/stoic-lamport-t601ql`. Left: T3 (waits on U4) and T11 (after the
> release). The phone test (U1) now also covers the SQLCipher swap (#9) and
> the sealed vault envelope (#8).

Status as of 2026-09-21. Branch `fix/biometric-ime-autofill` at `9631167`, CI
green, unmerged. Priorities: **P0** blocks a release, **P1** should ship with
it, **P2** soon after, **P3** when convenient.

## Waiting on the user

| # | Task | Why it waits |
|---|---|---|
| U1 | **Phone test** with [PHONE-TEST-CHECKLIST.md](PHONE-TEST-CHECKLIST.md): biometric unlock (P0), the keyboard in real apps, the two Autofill fixes, backup round-trip, auto-lock, large fonts | Needs a physical device with a fingerprint sensor. CI cannot enrol a fingerprint. |
| U2 | **Merge and release**: bump `0.2.1` / `versionCode 2` to `0.2.2` / `3`, merge, tag `v0.2.2` | A push to `main` runs the signed release workflow. |
| U3 | **Approve the Inter font download** (~1 MB, github.com/rsms/inter, SIL OFL) | Downloads need explicit approval. |
| U4 | **Decide on an on-screen idle auto-lock** (see T3) | Product decision. |
| U5 | Optionally delete the Android SDK in `AppData\Local\Android\Sdk` (~490 MB) | It is not cache, so it was kept. CI does all builds. |

## Agent-ready

### P1

- **T1. Login editor clears the TOTP secret.**
  `CredentialEditorScreen.kt` saves `totpSecret = ""` and never loads the
  item's value, so editing any login wipes a stored TOTP secret (for example
  one that arrived through a backup import). Preserve the loaded value and add a
  regression test. Found 2026-09-21 while writing these docs; not yet fixed.
- **T2. Verify biometrics on CI where possible.** (Done 2026-10-05, #3: the `Emulator fingerprint check` job.) The emulator job cannot enrol
  a fingerprint. Try `adb emu finger touch` after scripted enrolment (needs a
  screen lock set first) so unlock, cancel and wrong-finger paths get runtime
  evidence.

### P2

- **T3. Idle auto-lock (needs U4).** (Done 2026-10-06: U4 answered; "Lock after no taps" Off/1/5/15 min, default 5 min.) Today the vault only locks after the app
  leaves the screen; a vault left open on screen never locks.
  `VaultLifecycleObserver.onUserActivity()` is wired to
  `MainActivity.onUserInteraction()` but only cancels a timer that is never
  started in the foreground. Design: a foreground timer reset on interaction,
  with its own setting separate from "lock after leaving the app".
- **T4. Keyboard switch key.** Add a globe key (`switchToNextInputMethod`) so
  users are never stuck; pick a numeric layout for number fields; portrait and
  landscape sizing.
- **T5. Release-build smoke test.** (Done 2026-10-05, CI #52: the minified `internal` build passes the full emulator check.) Run the R8-minified `assembleInternal`
  build through the emulator check, since minification can break Moshi, Compose
  or the Autofill/IME services in ways the debug build hides.
- **T6. Backup round-trip test.** `BackupCodec` has unit tests, but export then
  import of a real vault with folders, tags, cards and identities has not been
  exercised end to end. Add an instrumented or emulator-driven test.
- **T7. Full TalkBack pass.** (Done in code 2026-10-05, #2; confirm on the phone test.) Switches, chips and tabs are fixed; check the
  remaining icon buttons, list rows and dialogs, and reading order in the
  editors.
- **T8. Batch `exportData()`.** The Security dashboard re-scan issues about
  three queries per item (N+1) off the main thread. Fine at typical sizes; use
  joins if vaults grow (audit finding b).

### P3

- **T9. Lint clean-up.** (Done 2026-10-05/06: #4, #5, #10.) The last report had 65 warnings, none functional: 39
  `GradleDependency`, 10 `UnusedResources`, 7 `ObsoleteSdkInt`, 3
  `AndroidGradlePluginVersion`, 2 `IconDipSize`, one each of `UnusedAttribute`,
  `RedundantLabel`, `OldTargetApi`, `DataExtractionRules`. Remove the unused
  resources and dead SDK checks; update dependencies as a separate, tested
  change.
- **T10. Unused imports** (Done 2026-10-05, #7; CI now checks.) left by the UI rewrite (compiler warnings only).
- **T11. Kotlin package rename.** Sources are still `com.example.*` while
  `applicationId`/`namespace` are `com.atomicvault.android` (audit finding d).
  Do it only as a dedicated, mechanical change after the release, never mixed
  with feature work.
- **T12. README.** (Done 2026-10-05.) It listed the stack and two build commands; add the CI-only
  workflow, how to download artifacts, and a pointer to `docs/`.

## Known limitations (not planned as bugs)

- The keyboard fills one field at a time (an IME sees one focused field), unlike
  Autofill, which fills username and password together.
- The keyboard cannot see a web domain, so it only suggests native-app logins.
- If the process is killed before the 45 s timer fires, a copied password stays
  on the clipboard.
- Emulator screenshots of vault screens are blank (`FLAG_SECURE`).
- Autofill inline suggestions and the app-link (level 4) trust path are
  implemented but have only been reviewed, not exercised on a device.

## Audit findings (from `AUDIT-REPORT.md`)

| Finding | Status |
|---|---|
| (a) Auto-lock is background-only | Fixed: "Lock after no taps" idle timer (T3) |
| (b) `exportData()` N+1 | Fixed (T8, batched export) |
| (c) `ClipboardHelper` dead `SDK_INT >= P` branch | Fixed; the helper was rewritten |
| (d) Kotlin packages still `com.example.*` | Open, deliberate (T11) |

## Done recently

See [CHANGELOG.md](CHANGELOG.md).
