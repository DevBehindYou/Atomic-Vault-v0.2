# Pending

## P0

### TASK-001 — Automated phone check on the user's phone

Status: OPEN
Why: Nothing has run on a real device yet.
Files: `tools/phone_check.sh` (PR #19), `.github/scripts/emulator_check.sh`
Action:
1. Merge PR #19 when its CI is green (or check out its branch).
2. On the computer with the phone attached: `gh auth login` once, then `tools/phone_check.sh`.
3. If it fails, read `phone-check/emulator-artifacts/` and the console's
   "app process" section. A `FATAL EXCEPTION` from another app is not ours.
Done when: the script prints "Automated phone check passed".
Never run `emulator_biometric_check.sh` or `emulator_screenlock_check.sh` on
the user's phone: they set a lock-screen PIN (`locksettings set-pin 1111`).

### TASK-002 — Manual phone test

Status: OPEN
Files: `docs/PHONE-TEST-CHECKLIST.md`
Action: The user (or a local agent with the user) ticks sections 1-7. New this
session: 4a screen-lock, 4b CSV, 4c history, 4d breach, 5 idle lock.
Done when: every box is ticked or a bug is filed with steps.

### TASK-003 — Review CI UI snapshots

Status: OPEN
Files: artifact `AtomicVault-UI-Snapshots-<run>` (Roborazzi, Robolectric renders; about 29 PNGs)
Action: `gh run download <run> -R DevBehindYou/Atomic-Vault-v0.2 -n AtomicVault-UI-Snapshots-<run>`,
then compare against `docs/design/ATOMIC-DESIGN-SYSTEM.md`. Note: emulator
screenshots are blank by design (FLAG_SECURE); the Roborazzi snapshots are the
ones to review.
Done when: issues are listed as tasks, or the snapshots are approved.

## P1

### TASK-004 — Release (user only)

Status: OPEN
Action: bump `versionName`/`versionCode` in `app/build.gradle.kts` (currently
0.2.1/2; the plan names 0.3.0, and 0.4.0 for the design system), merge the
work branch to `main`, tag. A push to `main` runs the signed release workflow.
Dependencies: TASK-001, TASK-002.

## P2

### TASK-005 — Kotlin package rename (T11)

Status: OPEN (blocked on TASK-004)
Action: rename `com.example.*` to `com.atomicvault.android.*` in one
mechanical PR after the release; keep the applicationId.

### TASK-006 — Root-cause ISS-001

Status: OPEN
Action: wait for the next occurrence; PR #18 now prints the app's exit reason.
