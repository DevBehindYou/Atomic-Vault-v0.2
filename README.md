# Atomic Vault

An offline, encrypted credential manager for Android (Kotlin, Jetpack
Compose, SQLCipher, Android Autofill Framework). No network permission,
no cloud sync, no analytics -- everything stays on-device.

This project started from a Google AI Studio scaffold; the unused
Firebase/Gemini/Google-Services/network scaffolding that shipped in that
default template has been removed, since none of it fits an offline-only
app. See `docs/` (if present) or the project's design/architecture notes
for the security model and roadmap.

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio.
2. Select **Open** and choose the directory containing this project.
3. Allow Android Studio to sync Gradle and fix any incompatibilities as it imports the project.
4. Before a real signed release, replace the placeholder `applicationId`
   in `app/build.gradle.kts` (currently `com.devbehindyou.atomicvault`)
   with your actual reverse-domain ID if this isn't it, and remove the
   `signingConfig = signingConfigs.getByName("debugConfig")` line.
5. Run the app on an emulator or physical device. Biometric-gated
   unlock/autofill features require a device or emulator with a biometric
   sensor configured.

## Notes for contributors

- `minSdk` is currently 27. Several features (inline autofill matching
  refinements, data-access auditing, Credential Manager/passkeys) are
  tiered by Android API level -- see the project's roadmap notes before
  assuming a feature is available on all supported devices.
- The Keystore-backed biometric flow (`keystore/BiometricGatedKeyStore.kt`)
  is the single source of truth for the vault's DEK across the main app
  and the autofill service. Don't add a second parallel key store --
  extend this one.
