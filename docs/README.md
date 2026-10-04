# AtomicVault project docs

| File | What it is |
|---|---|
| [CHANGELOG.md](CHANGELOG.md) | Every change on the `fix/biometric-ime-autofill` branch, by area, with commit ids, plus upgrade notes |
| [SESSION-LOG.md](SESSION-LOG.md) | What was asked, found and decided, in order; the user's standing directives; mistakes not to repeat |
| [DECISIONS.md](DECISIONS.md) | The design and security choices, and why |
| [TASKS.md](TASKS.md) | What is left, by priority, and what is waiting on the user |
| [ROADMAP.md](ROADMAP.md) | Where the project goes next, and what it will not do |
| [PHONE-TEST-CHECKLIST.md](PHONE-TEST-CHECKLIST.md) | What only a physical phone can verify |
| [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md) | Keyboard removal, Gboard-grade Autofill, every open bug and performance fix, by phase, plus differentiators |

Other files in the repo root that matter: `AUDIT-REPORT.md` (an earlier
static-only audit; parts of it were wrong, see the change log) and
`atomicvault_design_system_reference/` (the visual reference).

## Status (2026-10-04)

- Active branch: `claude/atomic-vault-analysis-1fj2ok`, which continues
  `fix/biometric-ime-autofill` with the work in
  [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md): the Atomic keyboard is
  removed, Autofill is rebuilt for Gboard, data-safety fixes, performance, 2FA
  codes, phishing guard, fill receipts. **Not merged, not phone-tested.** `main`
  is untouched.
- Next step: the phone test ([PHONE-TEST-CHECKLIST.md](PHONE-TEST-CHECKLIST.md)),
  then version bump to 0.3.0 / `versionCode 3` and merge.

## Working on this project

The development machine cannot build Android locally (no disk space), so
everything goes through GitHub Actions.

1. Work on a branch and push it. `Android CI` (`.github/workflows/android-ci.yml`)
   runs on every non-`main` branch and pull request: unit tests, lint, the debug
   APK, UI snapshot renders and an emulator check. It needs no secrets.
2. Watch the run and download what you need with the GitHub CLI (on Windows it
   is `C:\Program Files\GitHub CLI\gh.exe`, not on `PATH`):

   ```powershell
   gh run list --branch <branch> --limit 3
   gh run download <run-id> -n AtomicVault-UI-Snapshots-<n>   # rendered screens
   gh run download <run-id> -n AtomicVault-Debug-APK-<n>      # installable build
   gh run download <run-id> -n AtomicVault-Emulator-<n>       # emulator logs
   ```

   `<n>` is the workflow's run number (shown in the artifact list of the run).
3. The debug build installs beside the release app as
   `com.atomicvault.android.debug`.
4. `android-release.yml` runs on pushes to `main` and `v*` tags and needs the
   four `ANDROID_KEYSTORE_*` / `ANDROID_KEY_*` secrets. Do not push to `main`
   without meaning to release.

Before touching a screen, open the matching image in
`atomicvault_design_system_reference/` and check the result in the UI snapshot
artifacts, including the 360 dp / 1.5x font renders (`stress_*.png`).
