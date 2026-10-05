# AtomicVault

An offline password vault for Android. Logins, payment cards and identities
are encrypted on the phone and filled into other apps through Android
Autofill, which works with Gboard or any other keyboard. The app has no
internet permission.

## Technology

- Kotlin, Jetpack Compose, Material 3 (themed by the Atomic design system)
- SQLCipher for the vault database, with field-level AES-256-GCM on top
- Argon2id to derive the key from the master password
- Android Keystore and BiometricPrompt for fingerprint unlock
- Android Autofill framework (no custom keyboard: it was removed in 0.3.0)
- Coroutines, Moshi (generated adapters)
- `minSdk` 28, `compileSdk` / `targetSdk` 35

## Modules

| Module | What it is |
|---|---|
| `app` | The app (Kotlin packages are still `com.example.*`; the application id is `com.atomicvault.android`) |
| `autofilltest` | A small login form app the emulator check fills through Autofill |

## Build types

| Build | Command | Installs as | Notes |
|---|---|---|---|
| Debug | `./gradlew assembleDebug` | `com.atomicvault.android.debug` | Installs beside the release app |
| Internal | `./gradlew assembleInternal` | `com.atomicvault.android.internal` | Release settings with R8 shrinking, signed with the debug key, for testing minified behaviour |
| Release | `./gradlew assembleRelease` | `com.atomicvault.android` | Needs the release keystore; the build refuses to run without it and never signs with the debug key |

## How changes are verified

There is no local Android build in this project's workflow: everything is
built and tested by GitHub Actions.

- **`Android CI`** (`.github/workflows/android-ci.yml`) runs on every branch
  except `main` and on pull requests, with no secrets: a design-system check,
  unit and Robolectric tests, lint, the debug and minified APKs, rendered UI
  snapshots, and an emulator check on both APKs (create a vault, walk the
  app, restart, unlock, fill a login through Autofill).
- **`Android Release Build`** (`.github/workflows/android-release.yml`) runs
  on pushes to `main` and `v*` tags and signs the release with the
  `ANDROID_KEYSTORE_*` / `ANDROID_KEY_*` secrets. A push to `main` is a
  release.
- What CI cannot check (fingerprint, real Autofill in other apps, how the
  screens look on a phone) is in
  [docs/PHONE-TEST-CHECKLIST.md](docs/PHONE-TEST-CHECKLIST.md).

## Documentation

Start at [docs/README.md](docs/README.md): current status, how to download CI
artifacts, the change log, decisions, the implementation plan, open tasks and
the roadmap. The visual source of truth is
[docs/design/ATOMIC-DESIGN-SYSTEM.md](docs/design/ATOMIC-DESIGN-SYSTEM.md).
`AUDIT-REPORT.md` in the root is an older static audit kept for history;
parts of it were wrong (see the change log).
