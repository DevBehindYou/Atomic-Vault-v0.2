# Roadmap

Direction, in order. Nothing here is a promise; each item is only built if it is
useful and reliable, and the app stays small. Task-level detail is in
[TASKS.md](TASKS.md).

## Principles

1. **Useful, reliable, needed.** No decorative screens, fake statistics or
   features the app cannot back up. The design reference is a visual language,
   not a feature list.
2. **Runtime evidence.** A feature is not "working" until it has run: on the CI
   emulator, and for biometrics, Autofill and the keyboard, on a real phone.
3. **Offline and private.** No analytics, no telemetry, no network permission.
   Anything that would need the network is out of scope.
4. **Never lose user data.** Prefer creating a duplicate to overwriting; keep
   the master password path working through every migration.
5. **Native Android only.** Kotlin, Compose, SQLCipher, Keystore.

## 0.2.2 - release the current branch

- Phone test using the checklist; fix what it finds (biometrics first).
- Fix the editor dropping TOTP secrets (T1).
- Bump the version, merge to `main`, tag, let the signed release workflow run,
  verify the signature and the upgrade from 0.2.1 (biometric unlock is turned on
  again once; data is preserved).
- Release notes from [CHANGELOG.md](CHANGELOG.md), including the upgrade notes.

## 0.3 - polish and daily use

- Bundle the Inter font once approved, so type matches the reference.
- Idle auto-lock with its own setting, if approved.
- Keyboard: switch-keyboard key, numeric layout for number fields, better
  landscape sizing, optional haptics.
- Autofill: a way to link a login to a specific site or app by hand (trust
  level 1, currently unimplemented); a documented, tested policy for subdomains
  (for example whether `login.example.com` may use an `example.com` login),
  ideally using the Public Suffix List rather than string rules.
- TOTP: show the current code for a stored TOTP secret, as the reference does.
  Only if the secret storage is first made safe (T1).
- Backup and restore: an on-device round-trip test, clearer error messages,
  optional reminder to export.
- Accessibility: a full TalkBack pass; keep large-font and 360 dp snapshots in
  CI.

## 0.4 - platform health

- Test the minified release build in CI (R8), including the services.
- Update AGP, Kotlin, Compose BOM and the other dependencies together, then
  raise `compileSdk`/`targetSdk`. One change, fully tested, no feature work mixed
  in.
- Rename the Kotlin packages from `com.example.*` to `com.atomicvault.android.*`
  as a purely mechanical change (T11).
- Batch the security dashboard's reads (T8); consider a baseline profile for
  start-up.

## Later, only if wanted

These appear in the design reference but are **not planned**. Each would need a
real use case, real backing implementation and the user's agreement first.

- Hardware-backed passkeys / FIDO2 vault entries.
- Emergency access / inheritance.
- Printable emergency sheet (PDF export).
- Any "defence index", attestation or "air-gapped" claims without a real check
  behind them. The Privacy Proof screen is the model: every line is a check the
  app actually runs.

## Not doing

- Anything that needs internet access or accounts.
- Cloud sync. (An encrypted file the user moves themselves is the backup
  feature.)
- Analytics or crash reporting that leaves the device.
- Lowering `minSdk` below 28, or signing production with a debug key.
