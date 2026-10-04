# Implementation and optimization plan

Written 2026-10-03 from a full read of the code on `fix/biometric-ime-autofill`
(`6556be6`), `main`, the other branches, the CI history and every handover
document (this folder, `AUDIT-REPORT.md`, the Drive build log). Nothing here has
been compiled or run yet; each phase says how it gets proven.

Goal set by the user:

1. Remove the Atomic keyboard from the app entirely.
2. Make AtomicVault work smoothly with the Google keyboard (Gboard) and any
   other system keyboard, through Android Autofill.
3. Fix the bugs and performance problems found across the code.
4. Make the app clearly more useful and reliable than the alternatives.

---

## Progress

| Phase | Status | Commit | CI |
|---|---|---|---|
| 0. Baseline | Done | `049e85b` | Run #25 green (unit, lint, emulator) |
| 1. Remove the Atomic keyboard | Done | `a6c645f` | Run #26 green (emulator: restart + unlock via system keyboard) |
| 2. Data safety | Done | `c1bd5cc` | Run #27 green (unit, lint, emulator) |
| 3. Gboard Autofill | Done in code | `84e4fdc` + lint fix `6067d63` | #28 failed on one lint error (fixed); #33 green with runtime proof: Android received a locked response with authentication (`hasAuthentication`, `showing: true`), the "Autofill UI" popup was tapped, and "Fill with AtomicVault" opened (`1b8a860`) |
| 4. Performance + 2FA codes | Done in code | `d210431` | Run #30 green (after #29 test-expectation fix `3cf6f99`) |
| 5. Platform health | Partly: TOTP engine, Moshi codegen, dead backup branch closed. Open: SQLCipher artifact swap, SDK/AGP upgrade, `security-crypto` replacement, package rename | | |
| 6. Differentiators | Phishing guard `744055c`, fill receipts `6067d63`, India identity fields `4fc26e1`. change-password shortcut and backup check ("restore drill") done. Open: offline breach check (needs a dataset), QR transfer | | Run #30 green |
| 7. Atomic design system (UI/UX rebuild), ships as **0.4.0** | 7.0 done (plan, spec, [design canvas](https://claude.ai/artifact/KXY28fKQRhZcqpL4cgaGya)); 7.1 + 7.2 green (CI #36, emulator incl. Autofill tap); unlock, onboarding, Autofill screens, home and the new item detail pushed; editors, generator, health, settings, backup, privacy next | see CHANGELOG | |

Note on F11 (flaky test): run #23's failure did not reproduce in runs #24 and
#25. Failing tests are now printed in the CI log, so the next occurrence will
name the test.

---

## 0. How this plan is run

Same rules as the rest of `docs/` (see SESSION-LOG "standing directives"):

- One phase = one branch off the current integration branch = one pull request.
  Every PR must pass `Android CI` (unit tests, lint, debug APK, snapshots,
  emulator job) before the next phase starts.
- Data-safety work comes before features. A phase that changes stored data
  ships with a migration test that opens a vault created by the previous
  version.
- Nothing is called "working" without runtime evidence: CI emulator for
  everything it can reach, the phone checklist for biometrics, Gboard and
  browsers.
- `main` is touched only for a release (it triggers signed release builds).

Integration branch: `fix/biometric-ime-autofill` already contains 24 unmerged
commits of fixes that v0.2.1 users need. This plan builds on it rather than on
`main`. Proposed release: everything through Phase 3 ships together as
**0.3.0 (`versionCode 3`)** after one phone test.

---

## 1. What changes for the user

| Today | After this plan |
|---|---|
| A custom keyboard you must enable and switch to; it fills one field at a time and cannot see websites | No custom keyboard. Gboard (or any keyboard) shows AtomicVault chips in its suggestion strip; one tap + fingerprint fills username **and** password |
| The unlock screen forces its own keyboard with a fixed set of characters | The unlock screen uses the normal system keyboard in password mode (no learning, no suggestions) |
| Autofill only works if you used your fingerprint in the last 30 s; otherwise it shows nothing | A locked vault shows one "Unlock AtomicVault" chip; tap it, authenticate, pick the account |
| "Save to AtomicVault" is silently dropped outside the 30 s window | Saving always works: Android opens a short confirm screen with a fingerprint check |
| Autofill requires a fingerprint to be enrolled | Master password works as a fallback everywhere |
| Sign-up forms get nothing | A "Generate strong password" chip fills and saves a new password |
| 2FA codes must be copied by hand | A chip offers the current TOTP code on one-time-code fields |

---

## 2. Findings this plan fixes

IDs are used by the phases below. **P0** = data loss, lockout or security hole.
**P1** = broken feature or crash. **P2** = performance or correctness. **P3** = hygiene.

### Security and data safety

| ID | Pri | Finding | Where |
|---|---|---|---|
| B1 | P0 | **Master-password lockout.** Onboarding uses the system keyboard; Unlock only offers the in-app keyboard (ASCII letters plus a short symbol list). A password with `é`, `ü`, Devanagari, emoji or any missing symbol can be created but never typed again. When biometrics are reset (new fingerprint enrolled) the vault is unrecoverable. | `ui/unlock/UnlockScreen.kt`, `ui/components/LiquidGlassKeyboard.kt` |
| B2 | P0 | No Unicode normalization before Argon2. The same visible password can produce different bytes on different keyboards (composed vs decomposed `é`). | `crypto/Argon2Kdf.kt` |
| B3 | P0 | Onboarding is not covered by `FLAG_SECURE` (only LOCKED/UNLOCKED are), so the screen where the master password is created can be screenshotted, recorded and shown in Recents. | `MainActivity.kt` (the `shouldBeSecure` line) |
| B4 | P0 | Locking while a save/export runs: `lockVault()` closes the DB and zero-fills the DEK array the repository is still using. An item sealed at that moment is encrypted with an all-zero key and can never be opened, or the coroutine crashes. | `ui/VaultViewModel.kt` `lockVault()` |
| B5 | P0 | `VaultMetaStore` silently falls back to empty plain SharedPreferences if EncryptedSharedPreferences fails. The app then shows onboarding over an existing encrypted DB, and creating a vault fails. Device-to-device transfer on Android 12+ is still allowed (`allowBackup="false"` does not block it; `data_extraction_rules.xml` is an unused template), which is a likely trigger. | `keystore/VaultMetaStore.kt`, `AndroidManifest.xml`, `res/xml/data_extraction_rules.xml` |
| B6 | P0 | The login editor saves `totpSecret = ""`, wiping any stored TOTP secret on edit (TASKS T1). | `ui/editor/CredentialEditorScreen.kt` |
| B7 | P1 | AtomicVault's own screens are not excluded from Autofill. Our own service (or Google's) can offer to fill or save the master password and the editor fields, creating duplicate or bogus items. The service does not ignore its own package. | `MainActivity.kt`, `autofill/VaultAutofillService.kt` |
| B8 | P1 | KDF parameters are saved in the envelope but unlock always uses the compiled defaults. Any future tuning would lock every existing vault. | `ui/VaultViewModel.kt`, `keystore/VaultMetaStore.kt` |
| B9 | P1 | The Trust Ledger chain is ordered by millisecond timestamp, then a random UUID, and the read-last-hash + insert pair is not in a transaction. Two events in the same ms, concurrent writes (app + Autofill) or a clock change produce a false "chain broken". | `trust/TrustLedger.kt` |
| B10 | P1 | The inline-suggestion attribution intent is the fill auth intent. Long-pressing a Gboard chip (which should show "about this suggestion") starts the fill flow instead. | `autofill/VaultAutofillService.kt` `buildInlinePresentation` |
| B11 | P2 | Field detection: `idEntry.contains("pass")` matches `passport`, `compass`, `bypass`; HTML attributes (`type`, `autocomplete`) are ignored; new-password vs current-password vs one-time-code is not distinguished; invisible fields are not skipped. | `autofill/AssistStructureParser.kt` |

### Broken features and crashes

| ID | Pri | Finding | Where |
|---|---|---|---|
| F1 | P1 | Autofill shows nothing unless a strong biometric auth happened in the last 30 s (grace key). In practice suggestions appear only right after unlocking the phone with a fingerprint. | `keystore/BiometricGatedKeyStore.kt`, `VaultAutofillService.onFillRequest` |
| F2 | P1 | Saves outside the 30 s window are silently discarded after the user tapped "Save". | `VaultAutofillService.onSaveRequest` |
| F3 | P1 | Users without a fingerprint cannot use Autofill at all ("Arm autofill" = arm biometrics). | `ui/settings/SettingsScreen.kt`, keystore |
| F4 | P1 | Uncaught exceptions in `viewModelScope.launch(Dispatchers.IO)` blocks (create/update/delete/folder/tag) crash the app: a closed DB after auto-lock, a constraint failure, a corrupt field. | `ui/VaultViewModel.kt` |
| F5 | P1 | Search launches a new coroutine per keystroke with no cancellation; a slower older query can overwrite newer results (stale list). | `VaultViewModel.setSearchQuery/reloadPreviews` |
| F6 | P1 | The Security dashboard analyses payment cards and identities as logins; every one is flagged "EMPTY" and lowers the score. | `ui/security/SecurityDashboardScreen.kt`, `security/PasswordAnalysis.kt` |
| F7 | P1 | Backup import uses `db.insert` (which returns -1 instead of throwing). A tag that collides with the NOCASE unique index is silently dropped and its item links point at nothing; the restore still reports success. | `database/VaultRepositoryImpl.importReplace` |
| F8 | P1 | Import is replace-only with no safety copy: restoring an old backup silently deletes newer items. It also imports `biometricEnabled=true` while the Keystore is not armed. | same |
| F9 | P1 | One undecryptable field makes `getItem` throw, so a single damaged item breaks the whole dashboard scan and every backup export. | `VaultRepositoryImpl.getItem/exportData` |
| F10 | P2 | Any unlock failure, including a DB open error, is shown as "Incorrect master password". | `VaultViewModel.unlockWithPassword` |
| F11 | P2 | The latest CI run (#23, `6556be6`) is red: `testDebugUnitTest` failed, then passed when re-run in the same job. A timing-dependent test is flaky. | CI, see the `AtomicVault-CI-Reports-23` artifact |

### Performance

| ID | Pri | Finding | Where |
|---|---|---|---|
| P1 | P2 | Every list load and **every search keystroke** re-reads and AES-decrypts every username in the vault, plus one extra tag query per row (N+1). | `VaultRepositoryImpl.listPreviews` |
| P2 | P2 | Every Autofill request opens a new SQLCipher connection. The DEK is passed as a byte array, which (to be confirmed by measurement) makes SQLCipher run its own PBKDF2 key derivation on every open. | `database/VaultDatabase.kt`, autofill |
| P3 | P2 | Argon2id (64 MiB, t=3) runs in pure-Java BouncyCastle: typically seconds per unlock on mid-range phones. A native implementation produces identical output several times faster. | `crypto/Argon2Kdf.kt` |
| P4 | P2 | The Trust Ledger creates a new `SQLiteOpenHelper` and opens/closes the DB file for every single event, sometimes on the main thread (auth activities). | `trust/TrustLedger.kt` |
| P5 | P2 | The dashboard holds the whole decrypted vault (`CredentialPlain` with passwords) in Compose state for as long as the screen is open, and computes the analysis on the main thread. | `SecurityDashboardScreen.kt` |
| P6 | P3 | Moshi uses `KotlinJsonAdapterFactory` (reflection, pulls in `kotlin-reflect`) alongside KSP codegen. | `backup/BackupCodec.kt`, `app/build.gradle.kts` |
| P7 | P3 | The password-strength estimate is naive (`Password123!` scores ~78 bits, "strong"). | `PasswordAnalysis.estimateEntropyBits`, `PasswordGenerator` |

### Platform and hygiene

| ID | Pri | Finding |
|---|---|---|
| H1 | P1 | `net.zetetic:android-database-sqlcipher` 4.5.4 is the deprecated artifact. Its replacement is `net.zetetic:sqlcipher-android`. Also check the native libs for 16 KB page-size alignment, which Google Play requires for new uploads (`zipalign -c -P 16` / APK Analyzer). |
| H2 | P1 | `targetSdk 35`: check the Play Console for this year's minimum target API before the next upload. |
| H3 | P2 | `androidx.security:security-crypto` is deprecated upstream. |
| H4 | P3 | Dead code: `AmbientVaultBackground` (unused), the `vault` table (never written), `audit_log_entry` (written on every change, never read, grows forever), `autofill_service_config.xml`, unused `parent_id` folder nesting. |
| H5 | P3 | Unmerged branch `fix/v0.2-audit-and-bugfixes` (3 backup-hardening commits, Sept 5) is not mentioned anywhere. Diff it against current `BackupCodec`/`BackupFile` and either port it or close it. |
| H6 | P3 | `AUDIT-REPORT.md` on `main` still reads as authoritative; parts are wrong (see CHANGELOG). Mark it superseded. |

---

## 3. Target architecture

### 3.1 One vault session, shared by the app and Autofill

Today the DEK and DB live inside `VaultViewModel`; Autofill and the auth
activities each open their own connection. Replace this with one
process-wide object:

```
VaultSession (singleton, process scope)
  state: Locked | Unlocked(dek, db, repository, previewCache)
  unlock(dek)                 -> opens DB once, builds preview cache
  suspend fun <T> use(block)  -> runs block under a read lease
  lock()                      -> waits for leases to finish, then closes DB and zeroes DEK
  observe(): StateFlow<State>
```

- `use {}` with a `Mutex`/lease count fixes **B4**: lock waits for in-flight
  writes instead of pulling the key out from under them.
- `VaultViewModel` becomes a thin UI adapter over `VaultSession`.
- Autofill reuses the open session when the vault is unlocked (no new
  connection, no key derivation: fixes **P2** in the common case) and opens a
  short-lived one only after authenticating while locked.
- Auto-lock (`VaultLifecycleObserver`) calls `VaultSession.lock()`, so the
  same timeout governs Autofill.

### 3.2 Keystore: one per-use key, no grace key

The grace key (30 s, no UI) exists only because the current design tries to
match credentials without showing anything. The new Autofill flow (3.3) always
authenticates in an activity, so:

- Keep the **unlock key** (`setUserAuthenticationParameters(0, BIOMETRIC_STRONG)`,
  invalidated by new enrolment), used through `BiometricPrompt` + `CryptoObject`.
- Delete the grace key and `tryRevealWithoutPrompt()`. Purge its alias on
  upgrade. The unlock key is untouched, so users do **not** need to re-enable
  biometrics again.
- Master-password fallback: every auth screen offers "Use master password".

This removes the only no-UI key release in the app, a real security
simplification.

### 3.3 Autofill flow designed around Gboard's suggestion strip

Android 11+ keyboards that support inline suggestions (Gboard does) show the
Autofill service's chips inside the keyboard's strip. Android 9–10 get the
classic dropdown. Same service, same code path.

```
onFillRequest(structure, inlineRequest)
 ├─ ignore own package, ignore if no login/OTP/new-password field found
 ├─ vault UNLOCKED (session) ─────────────► datasets: one chip per matching account
 │                                           "github.com · ashu@…"  (+ pinned "AtomicVault" chip)
 │                                           each dataset: values filled after a per-fill
 │                                           fingerprint (default) or directly (user setting)
 └─ vault LOCKED ─────────────────────────► FillResponse.setAuthentication(fieldIds, intent,
                                             dropdown + inline "Unlock AtomicVault" chip)
                                               │ tap
                                               ▼
                                     AutofillAuthActivity
                                       BiometricPrompt (unlock key) or master password
                                       → VaultSession.unlock → match
                                       → returns FillResponse with the account chips
                                         (rebuilt from EXTRA_INLINE_SUGGESTIONS_REQUEST),
                                         or a Dataset directly if exactly one match
```

Details that make it feel right in Gboard:

- **Inline chips:** `InlineSuggestionUi` v1 with a title (site), subtitle
  (masked username), start icon and content description. Respect
  `maxSuggestionCount`, reuse the last `InlinePresentationSpec` for extra
  chips, and use the API 33 `Presentations`/`Field` builders where available
  (the `setValue(…, inline)` overloads are deprecated there).
- **Attribution:** a separate PendingIntent that opens AtomicVault's Autofill
  settings (fixes **B10**).
- **Pinned chip** (`pinned = true`): "AtomicVault" at the strip's end opens a
  search picker for manual choice. It's also used for `FLAG_MANUAL_REQUEST`
  (the user long-pressed a field and chose Autofill).
- **One tap fills username and password together.** Each dataset sets both
  `AutofillId`s.
- **Sign-up forms** (`autocomplete="new-password"`, hint `newPassword`): a
  "Generate strong password" chip fills a generated password into the new and
  confirm fields and pre-arms the save.
- **2FA fields** (`one-time-code`, hint `smsOTPCode`/`2faAppOTPCode`): a chip
  with the current TOTP code for the matching item. Needs the TOTP engine
  (Phase 5) and B6 fixed first.
- **Save always works:** `onSaveRequest` returns
  `SaveCallback.onSuccess(IntentSender)` (API 28+) to a small
  `AutofillSaveActivity` that authenticates, then runs the existing
  `AutofillSave` decisions (update the same username, never overwrite another
  site). This fixes **F2**.
- **Two-page logins** (email first, password next): `SaveInfo.FLAG_DELAY_SAVE`
  plus `clientState` carries the username into the password page's request.
  SPAs: `FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE`.
- **Browsers:** with Chrome, the user must pick "Autofill using another
  service" in Chrome's settings. The onboarding guide detects Chrome and walks
  the user through it. For Firefox/Brave/Edge/Samsung Internet, test on device
  and add `<compatibility-package>` entries to `autofill_service.xml` only for
  browsers that need them.
- **Matching** stays as on the fix branch (domain decides for web, package only
  for native screens, level 0 never auto-offered), plus a look-alike-domain
  guard (Phase 6).

### 3.4 Typing secrets with Gboard safely

- Master password, card PIN/CVV: `BasicSecureTextField` (Compose foundation)
  or the existing field with `KeyboardType.Password`/`NumberPassword`.
  Gboard does not learn from or suggest on password-type fields.
- Other sensitive text (notes, TOTP secret, custom fields marked sensitive,
  card number): add `IME_FLAG_NO_PERSONALIZED_LEARNING` through Compose's
  `InterceptPlatformTextInput` (edits the `EditorInfo`), and turn autocorrect
  off. Verify on Gboard that incognito mode engages.
- Exclude the whole app window from Autofill
  (`importantForAutofill = noExcludeDescendants` on the decor view) and ignore
  our own package in the service (fixes **B7**).
- `FLAG_SECURE` from the first frame, for every status including onboarding
  (fixes **B3**).

---

## 4. Phases

Each phase lists its changes, its tests, and what "done" means.

### Phase 0: Baseline (small, first)

1. Find and fix the flaky unit test (**F11**). Download `AtomicVault-CI-Reports-23`,
   identify the test, and remove the timing dependence (inject a clock or
   test dispatcher). Do not retry or skip it.
2. Port or close `fix/v0.2-audit-and-bugfixes` (**H5**).
3. Add a **vault fixture test**: commit a small vault DB + envelope created by
   the current code (test key, test password) under `app/src/test/resources`,
   and a Robolectric test that opens it. Every later phase must keep it green.
   This is the guard against breaking existing users.
4. Mark `AUDIT-REPORT.md` superseded (**H6**).

Done when: CI green twice in a row on an unchanged commit.

### Phase 1: Remove the Atomic keyboard

**Delete**
- `keyboard/AtomicVaultInputMethodService.kt`
- `keyboard/KeyboardCredentialAuthActivity.kt`
- `keyboard/KeyboardRevealCoordinator.kt`
- `ui/components/LiquidGlassKeyboard.kt`
- `res/xml/method.xml`
- the `<service … BIND_INPUT_METHOD>` and `KeyboardCredentialAuthActivity` entries in `AndroidManifest.xml`
- the `InputMethodService` keep rule in `proguard-rules.pro`
- the keyboard snapshot tests in `UiSnapshotTest` and the keyboard disclosure tests in `RegressionUiTest`/`UiStressSnapshotTest`
- the IME steps of `.github/scripts/emulator_ime_check.sh` (rename to `emulator_check.sh`; keep the launch, create-vault and navigation walk) and the matching job text in `android-ci.yml`

**Change**
- `UnlockScreen.kt`: replace the in-app keyboard and the dot display with a
  secure password field (system keyboard, IME action "Unlock", show/hide
  toggle). Remove the "Typed on this keyboard only" strip. Fixes **B1**.
- `SettingsScreen.kt`: remove the "Atomic Keyboard" section and its
  disclosure dialog. Replace it with an Autofill status card (Phase 3 fills it
  in).
- `CredentialMatcher.kt`, `BiometricGatedKeyStore.kt`: remove keyboard
  references in code and comments. Keep the "a package match never reveals a
  domain-bearing item" rule; it still protects Autofill on native screens.
- `TrustLedger`: keep `source = "keyboard"` readable so old entries still
  display; nothing new writes it.
- `PrivacyChecks`/`ManifestPrivacyClaimsTest`: add a check that the manifest
  declares **no** `BIND_INPUT_METHOD` service (a new, provable privacy claim).
- `Argon2Kdf`: normalize the password to NFC before deriving (**B2**). On
  unlock, try NFC first, then the raw bytes. If only the raw form works,
  re-wrap the DEK under the NFC-derived key in the same step, so the vault
  migrates silently.
- `MainActivity`: `FLAG_SECURE` always (**B3**); exclude the window from
  Autofill (**B7**, app side).
- Docs: CHANGELOG, DECISIONS (new entry: "keyboard removed, Autofill is the
  only fill path"), ROADMAP, PHONE-TEST-CHECKLIST section 2 rewritten for
  Gboard.

**Upgrade path:** users who had Atomic Keyboard selected fall back to the
system default keyboard automatically when the service disappears. Show a
one-time notice after the update: "Atomic Keyboard was removed. AtomicVault
now fills inside your normal keyboard; turn on Autofill to use it."

**Tests:** unlock with a password containing `é`, `ü`, `न`, an emoji and every
symbol (unit test through the ViewModel); a decomposed `é` unlocks a vault made
with a composed one; the fixture vault still opens; the manifest has no IME; the
emulator job types the master password with the system keyboard.

Done when: no `InputMethod` reference remains (`grep` in CI), the emulator walk
passes, and APK size drops.

### Phase 2: Data-safety and crash fixes

| Fix | Change | Test |
|---|---|---|
| B4 | Introduce `VaultSession` (3.1); `lockVault()` → `VaultSession.lock()` waits for leases | A test that starts a slow `createItem`, locks mid-way, and asserts the item is either fully saved and readable or not saved at all |
| B5 | `VaultMetaStore`: no silent fallback. On failure, show a recovery screen ("This device can't open the vault key store. Restore from backup or reset.") and never overwrite the DB file. Add `dataExtractionRules` excluding everything from cloud backup **and** device transfer | Robolectric: an injected failing prefs factory shows the recovery state; manifest test asserts the rules are referenced |
| B6 | Editor loads and preserves `totpSecret`, and later gets a field for it | Edit a login with a TOTP secret, save, re-open, secret intact |
| B8 | Parse `kdf_params_json` on unlock; reject unknown algorithms | Fixture vault with non-default params unlocks |
| B9 | Ledger: `INTEGER PRIMARY KEY AUTOINCREMENT seq`; order by `seq`; read-last + insert in one transaction; a single cached helper (also **P4**); writes on IO; DB version bump with a migration that assigns `seq` in the current order | Concurrent writes from 4 threads produce a valid chain; same-ms events verify |
| F4 | A `CoroutineExceptionHandler` in the ViewModel shows errors in the UI; every repository call goes through `VaultSession.use {}` and returns a `Result` | Delete after lock reports "Vault is locked" and does not crash |
| F5 | Query as `StateFlow` → `debounce(150 ms)` → `mapLatest` over the in-memory preview cache (**P1**) | Fast typing ends with the right results |
| F6 | Dashboard analyses `LOGIN` items only; cards/identities get their own checks (expiry date, empty number) | A vault with 2 cards and 1 strong login scores 100 |
| F7, F8 | Import uses `insertOrThrow` (any failure rolls back); before a replace, keep an encrypted in-app snapshot of the current vault for one undo; add a "Merge" mode (keep the newer `updated_at`); never import `biometricEnabled` | Collision backup rolls back fully; restore → undo returns the original vault |
| F9 | Field-level decryption errors mark the item "damaged" instead of throwing; export includes everything else and reports the count | A corrupted blob in one item: the dashboard and export still work |
| F10 | Distinguish a wrong password (GCM tag failure on the DEK unwrap) from DB/IO errors | Unit test with a corrupt DB file |

Done when: all of the above tests pass, plus the Phase 0 fixture test.

### Phase 3: Autofill that works with Gboard

Implements 3.2 and 3.3.

1. `AssistStructureParser` rewrite (**B11**). Rank signals: `autofillHints` >
   `htmlInfo` (`type`, `autocomplete`, `name`) > `inputType` variations >
   id/hint text as whole-word matches (`password`, `passwd`, `pwd`; no bare
   `pass`). Skip invisible and disabled nodes. Classify fields as
   USERNAME / CURRENT_PASSWORD / NEW_PASSWORD / OTP. Record `webScheme` (warn on
   `http`). Make it pure Kotlin over an interface so it can be tested with
   fake view trees.
2. Single per-use key; delete the grace key with an upgrade purge (**F1**).
3. Locked flow: response-level authentication chip; `AutofillAuthActivity`
   with biometric or master password (**F3**); returns datasets.
4. Unlocked flow: datasets from the session's preview cache, with per-fill
   biometric by default and a setting "Fill without fingerprint while the
   vault is unlocked".
5. Inline UI: chips, pinned AtomicVault chip, correct attribution (**B10**),
   spec and count handling, API 33 builders.
6. Save: `onSuccess(IntentSender)` → `AutofillSaveActivity` (**F2**);
   `FLAG_DELAY_SAVE` with `clientState` for two-page logins.
7. New-password chip with the generator.
8. Ignore requests from our own package (**B7**, service side).
9. Settings: an Autofill status card showing whether AtomicVault is the
   selected service (`AutofillManager.hasEnabledAutofillServices()`), a
   button for `ACTION_REQUEST_SET_AUTOFILL_SERVICE`, and a Chrome-specific
   step. Remove "Arm autofill": Autofill no longer depends on biometrics.

**Tests**
- Unit: the parser over fixture trees (Google login, a two-step login,
  sign-up, OTP, `passport` and `compass` fields); the dataset builder (counts,
  specs, values); the save decisions (existing `AutofillSaveTest`).
- Emulator (new job, API 30+ `google_apis_playstore` image, which ships
  Gboard): a tiny test login app in `androidTest`. Set AtomicVault as the
  service with
  `adb shell settings put secure autofill_service <pkg>/<service>`, focus the
  field, and assert the "Unlock AtomicVault" chip appears in the keyboard
  strip (uiautomator dump). Then authenticate with the master password and
  assert both fields are filled. Run the save path the same way.
- Phone checklist: rewrite section 3 for Gboard (inline chips, unlock chip,
  new-password chip, two-page login, save after hours of idle, Chrome setting).

Done when: on the phone, Chrome and one native app fill with Gboard chips
from a locked state, and save works an hour after the last unlock.

### Phase 4: Performance

| Item | Change | Measure |
|---|---|---|
| P1 | Preview cache in `VaultSession`: decrypt usernames once at unlock, update on create/update/delete; tags in one grouped query | List and search under 16 ms per keystroke with 1,000 items (benchmark test) |
| P2 | Session reuse first. Then measure DB open time; if key derivation shows up, move to the raw-key form `x'…'` with a `PRAGMA rekey` migration (fixture-tested) | Open time before and after, logged in a debug benchmark |
| P3 | Native Argon2id (e.g. argon2kt, after approval), verified against the existing known-answer test so output is byte-identical | Unlock time on the phone, before and after |
| P4 | Ledger single helper + IO (done in Phase 2) | — |
| P5 | Dashboard computes on `Dispatchers.Default` and keeps only ids + issue flags; passwords are dropped right after analysis | Heap dump shows no `CredentialPlain` list retained |
| P6 | Moshi codegen only; remove `KotlinJsonAdapterFactory` and `kotlin-reflect` | APK size; backup round-trip test |
| — | Baseline profile for start-up; R8 full mode; run the minified `internal` build through the emulator job (TASKS T5) | Cold start time; services work when minified |

### Phase 5: Platform health

- Migrate to `net.zetetic:sqlcipher-android` (**H1**) behind `VaultDatabase`;
  the fixture vault must open unchanged. Confirm 16 KB alignment.
- Raise `targetSdk`/`compileSdk` to the level Play requires (**H2**), with
  AGP/Kotlin/Compose BOM upgraded together. This is one PR with no features
  mixed in.
- Replace `security-crypto` (**H3**): store the envelope in plain private
  prefs (it is already ciphertext under the Argon2 KEK) or wrap it with our own
  Keystore AES key. Migrate once, with a fixture test.
- Remove dead code (**H4**): the `vault` table, `audit_log_entry` (or prune and
  display it), `AmbientVaultBackground`, `autofill_service_config.xml`.
- TOTP engine (RFC 6238, SHA-1/256/512, 6–8 digits) with RFC test vectors, a
  live code in the item view, and the Gboard OTP chip from 3.3.
- Kotlin package rename `com.example.*` → `com.atomicvault.android.*` (TASKS
  T11), as a mechanical, separate PR.

### Phase 6: Differentiators

See section 6. Each one is its own PR, after the release.

### Phase 7: Atomic design system

Rebuild every screen on the Atomic "Technical Editorial" design system
(`docs/design/ATOMIC-DESIGN-SYSTEM.md`): tokens, fonts, components,
navigation, item detail view, states, responsive layouts and accessibility.
Full plan in section 8. UI layer only; no change to crypto, storage or
Autofill logic.

---

## 5. Risks and how they are contained

| Risk | Containment |
|---|---|
| Breaking existing vaults (NFC, KDF params, rekey, library swap) | Phase 0 fixture vault; every crypto/storage change keeps the old path readable and migrates forward in one transaction; master password always works |
| Gboard/inline behaviour differs across Android versions | Same datasets also carry the dropdown presentation; inline is additive; emulator job on API 30 and 34 images; phone test on the user's Android 14 Xiaomi |
| Chrome needs a user setting | In-app guide; documented in the checklist |
| Removing the grace key changes UX | The unlock chip replaces it, and the user is never left with "nothing shows" |
| Users relying on the old keyboard | System falls back to the default IME; one-time notice |
| Biometric re-enrolment | Only the unlock key is kept; there is no second forced re-arm this release |

---

## 6. Making AtomicVault different and more useful

The market: Bitwarden, Proton Pass and 1Password are cloud-synced;
KeePassDX is offline but dated and file-based; Google Password Manager is
built in but tied to a Google account. AtomicVault's honest edge is
**offline, no network permission, and proof instead of promises**. Build on
that.

Ranked by usefulness and how well each fits the app:

1. **Phishing guard at fill time.** Before filling, compare the requesting
   domain with the item's: look-alike detection (edit distance, `rn`→`m`,
   punycode/IDN homographs, `login-paypal.com` vs `paypal.com`) and a
   "first time this login is used on this site/app" warning. Most managers
   simply don't offer the chip. AtomicVault can explain *why*.
2. **Offline breached-password check.** Ship a compact Bloom filter of the
   most common breached passwords (from the Pwned Passwords corpus) in the
   APK or as an optional file the user imports. Flag breached passwords in the
   dashboard with zero network access. Pair it with a proper strength model
   (zxcvbn-style) to fix **P7**.
3. **Fill receipts.** The Trust Ledger already exists. Show "this login was
   filled into: Chrome / github.com, 3 times" per item, using the hashed
   targets resolved while unlocked. It answers "where did my password go?"
4. **Change-password assistant.** For reused, weak or breached items, a
   button opens the site's `/.well-known/change-password` page in the
   browser (no network permission needed in our app). The new password is
   generated and saved through the normal Autofill save.
5. **India-ready item types.** Templates for Aadhaar, PAN, bank account + IFSC,
   UPI ID, DigiLocker and vehicle RC, with masking and copy-one-field. Add a
   hard warning never to store a UPI PIN or an OTP. This is directly useful
   for the home market and rare in global apps.
6. **Air-gapped device-to-device transfer.** Move an encrypted vault between
   your own phones with animated QR codes (camera, no network, no cloud). It
   keeps the "no INTERNET permission" claim intact; LAN sockets would break it.
7. **Restore drills and a recovery kit.** "Test my backup": decrypt and count
   the items without replacing anything. Add a printable recovery sheet (the
   design reference has one) and gentle reminders when the last export is old.
8. **Verifiable build.** The Privacy Proof screen shows the APK signing
   certificate's SHA-256 so users can compare it with the published release
   hash. Later, aim for reproducible builds.
9. **Duress/travel mode (later).** A second password opens a decoy vault, or
   selected items are hidden while travelling. Only after everything above is
   solid; it adds real complexity.

Explicitly not doing (unchanged): cloud sync, accounts, analytics, network
access, fake "defence index" style metrics.

---

## 7. Decisions needed from the user

1. **Fill policy while unlocked:** fingerprint on every fill (recommended
   default) or fill directly while the vault is unlocked, as a setting.
2. **Release grouping:** ship Phases 0–3 together as 0.3.0 after one phone
   test (recommended), or release the current fix branch as 0.2.2 first.
3. **New dependencies:** native Argon2 (performance), a strength-estimation
   library (accuracy), the Inter font (pending, TASKS U3).
4. **Differentiators:** which of section 6 to start with. Recommended: the
   phishing guard, then the offline breach check.
5. ~~Default theme~~ **Decided (user, 2026-10-04):** paper (light) by
   default, with the spec's dark variant and "Match system".
6. ~~When Phase 7 ships~~ **Decided:** phone-test and release 0.3.0 first
   (the functional work, up to commit `cd7b0d0`), then the redesign as
   **0.4.0**.
7. ~~Item detail view~~ **Decided:** items open in a read view with `EDIT`.

---

## 8. Phase 7: Atomic design system (full UI/UX rebuild)

Source of truth: [design/ATOMIC-DESIGN-SYSTEM.md](design/ATOMIC-DESIGN-SYSTEM.md)
("Technical Editorial", v1.0, October 2026). The spec is taken from Atomic
Notes (a Flutter app and a website). AtomicVault is native Kotlin with Jetpack
Compose, so section 14.3's Flutter tokens are translated to Compose below.
Where the spec and the current code disagree, the spec wins, except for the
security rules in 8.3, which the spec does not cover.

This is a rebuild of the product's look and interaction, not a recolour. Every
screen is re-specified in 8.7. Behaviour that works today (crypto, storage,
Autofill, backup, Trust Ledger) is not touched. The UI layer is rebuilt on top
of the same view models.

### 8.1 What the spec asks for (extracted)

| Area | The rule, in one line |
|---|---|
| Principles | Ink on paper; **one** accent (Signal `#3A2FF0`, under 5% of a screen); hard edges and honest depth; labels read like instruments; headlines are posters; show the real state (numbers, not adjectives); calm by default, loud on purpose |
| Colour | `ink #15171B`, `paper #F4F5F1`, `white` cards, `surface #EDEEE8` panels, `signal`; `slate` secondary text; `line` decorative hairlines only; `signal-light #8F88FF` is the only accent allowed on ink; Material error reds as tokens; no gradients, no pure black |
| Type | Display **Bebas Neue** (uppercase, line height 0.95–1.05, titles, buttons, hero numbers). Body **Hanken Grotesk** (sentence case, ≥ 15 sp). Mono **JetBrains Mono** (uppercase tracked labels, counters, timestamps, ≥ 12 sp). Numbers that change are mono; dates are ISO `2026-10-04 21:44` |
| Space | 4 dp grid; key steps 4 · 8 · 12 · 16 · 22 · 24 · 32 · 44 · 56; app margin 16 dp; card padding 16 dp |
| Shape | Radius 4 (default), 6, 8, pill 999, sheet tops 28. **Nothing 12–16 dp "soft"** |
| Borders | 1 dp `line` hairline · 1 dp ink rule (under headers and titles) · 1.5 dp ink structure (cards, chips) · 2 dp ink controls (buttons, inputs) · 2 dp Signal selected · 2 dp error danger · 4 dp left priority bar |
| Elevation | Solid offset shadows, **0 blur**: 2/3/4/5/6/8 dp, ink by default, Signal for "the recommended one". Card hover lifts, button press sinks 2 dp and loses its shadow |
| Icons | Material Symbols **Outlined** only, 20–24 dp; tiles 40 dp with a 1.5 dp ink border; "→" as a text arrow; no emoji, no mixing filled and outlined |
| Motion | press 120 ms · state 150 · toggle 200 · enter 350 · reveal 500; `ease` or linear only; **no springs or bounce**; reduced-motion keeps only short fades |
| Components | Button set (Primary, Solid, Ghost, Ghost-on-dark, Light-on-Signal, Destructive, Destructive icon, Text action); chips, status pills, badges, unread dot; white cards vs surface panels vs ink modules; settings rows; danger zone; inputs with mono labels above; Material-sized toggle; stepper; segmented toggle; bottom bar with **one** ink pill; bottom sheets with a mono label and a full-width primary |
| States | Loading = mono caps text with an ellipsis (plus a 2 dp ink bar for long waits, no spinners on content). Empty = a surface panel with one sentence and the next step. Error = what happened and how to fix it, under the control. Destructive confirm states the target and the numbers. Success is said in words with real numbers |
| Accessibility | AA contrast table (never Signal text on ink, never orange text on paper); 2 dp Signal focus ring; 48 dp touch targets; colour never alone; 200% font scale without clipping; uppercase applied by style so screen readers read words |
| Copy | Short declarative sentences; caps verb+object buttons (`COPY PASSWORD`); mono caps noun labels; `·` joins parts, `/` pairs state (`VAULT / UNLOCKED`), `→` means next; say what a destructive action does and that it is final |

### 8.2 Where the app is today (audit)

The current UI follows the earlier `atomicvault_design_system_reference/`
(dark, "Liquid Glass", D2). It is the opposite of the new spec on almost every
axis.

| Area | Today | Gap |
|---|---|---|
| Colour | `AtomicColors` is a global object of ~50 `mutableStateOf` properties, dark `#131313` by default. Only 24 are read; many are aliases set to the same value (`Cyan`, `Indigo`, `Purple`, `Bg`, `DarkBg`, `DarkAccent`...). The accent is the foreground colour (white or black), so there is **no** accent. Green `Success` is used 20 times | Replace with an immutable palette passed through a `CompositionLocal`; add Signal; drop the aliases; the spec has no success green (use ink plus a Signal check) |
| Type | System font, bold 24 sp titles, no display face or mono face (`Type.kt`) | Bundle the three families (8.4); semantic styles |
| Shape | Radius 6 / 8 / 12 / **16** / 24 | 4 / 6 / 8 / 28 / pill; 12–16 is explicitly banned |
| Depth | `LiquidGlassSurface` (38 uses with `AmbientVaultBackground`), flat fills, hairline borders | Ink borders and hard offset shadows |
| Motion | `GlassSpring` is `DampingRatioMediumBouncy`; custom easing curve | Bounce is banned; use `ease` timings; no reduced-motion handling today |
| Icons | `Icons.Default`/`Filled` (41 uses) | Outlined only |
| Magic numbers | 90 raw `.dp` literals in `ui/` | Tokens only |
| Big files | `CredentialEditorScreen` 732 lines, `SettingsScreen` 650, `NavGraph` 545; three editors repeat the same scaffold | One editor scaffold; split settings into sections |
| Flow | Tapping an item opens the **editor**: there is no read view, so viewing a password risks editing it | Item detail view first, edit as an explicit mode (8.7) |
| States | Ad hoc strings ("No events recorded yet.", "No folders created."), no shared loading or error component | Shared state components (8.8) |
| Outside the app | `VaultAuthActivity`, `AutofillSaveActivity` and `PhishingWarningActivity` lay themselves out by hand; the Autofill dropdown uses `simple_list_item_1` | Same components; a branded dropdown layout |
| Tests | 3 Roborazzi snapshot classes; `RegressionUiTest` finds 3 nodes by text; the emulator script waits for about 10 visible strings ("Create vault", "Unlock", "Fill with AtomicVault"...) | Uppercase rendering changes those strings, so the tests move to test tags **before** any visible change (7.1) |

Worth keeping: the component names (`AtomicTextField`, `AtomicPrimaryButton`,
`AtomicTopBar`, `AtomicBottomNav`, `IconTile`, `SectionLabel`,
`AtomicSwitch`), the single-dialog decision (D8), all test tags, the four
real top-level destinations, and theme preference storage outside the vault.

### 8.3 How the spec applies to a password vault (deliberate adaptations)

1. **Ecosystem concepts are mapped, not invented.** Energy, Coins, sync and
   tiers do not exist in an offline vault and will not be added (D2: no mock
   features). Their *patterns* carry over to real vault concepts:

   | Spec pattern | AtomicVault use |
   |---|---|
   | Energy card (ink module, hero number, bar) | **Vault health** card on Health and Home: `92 / 100`, bar, one-line reason |
   | Storage tiles ("ON DEVICE / IN CLOUD") | `42 ITEMS · ON THIS PHONE` / `0 · CLOUD: NONE`, which makes the offline promise visible |
   | Session card (`SESSION / ACTIVE`) | `VAULT / UNLOCKED`, auto-lock rule, `LOCK NOW` |
   | Danger zone, two-key danger | Restore-and-replace, delete item, reset vault |
   | Status pill `ON` / `ARMED` | Autofill `ON`, fingerprint `ARMED`, screen protection `ON` |
   | Fact sheet (`dt`/`dd`) | Privacy proof, backup details, item metadata |
   | Verify block, code wells | Trust Ledger hashes, APK signing certificate SHA-256 |
   | Priority left bar | Health findings: critical `error`, high `energy-high`, normal Signal |
   | Physics vocabulary | Used sparingly, for example "Atom mark" in the icon. No new jargon for security functions |

2. **The bar colours keep their meaning.** Orange and plum mean energy in the
   family, so the password-strength bar does not reuse them: weak `error`,
   fair ink, strong Signal, always with the word next to it.
3. **Secrets are never uppercased.** Passwords, usernames, card numbers, 2FA
   codes and notes are shown in JetBrains Mono, **in their real case**. Mono
   also tells `0/O` and `l/1/I` apart, which fixes real misreads when typing a
   password onto another device.
4. **User-named things keep their case and script.** Item titles are user
   data, so they use Hanken Grotesk 700, not uppercase Bebas. Bebas Neue and
   Hanken Grotesk have no Devanagari: a Hindi title falls back to the system
   font. Display is for app-authored chrome only.
5. **Uppercase at render time.** Compose has no CSS `text-transform`, so an
   `AtomicText` wrapper uppercases the display string and keeps the semantics
   label in normal case. TalkBack reads "Unlock", not "U-N-L-O-C-K", and
   stored strings stay unchanged.
6. **Fonts are bundled, never downloaded.** Downloadable fonts would contact
   Google, which breaks the no-network promise. The three families are OFL;
   variable `.ttf`s go in `res/font` (about 0.5 MB) with the licence in
   `assets/licenses/`. This resolves D9: Inter is no longer needed.
7. **Accent:** keep Signal, for family resemblance. The spec allows a sibling
   accent, but nothing argues for one here.
8. **Dark theme:** the spec is light-first and defines a dark variant (§13.9:
   ink background, `#1E2026` cards, paper text, `signal-light` accent).
   Proposal: paper is the default for new installs; existing users keep dark
   through the spec's dark variant; add "Match system". (Decision 5 in
   section 7.)
9. **`FLAG_SECURE` stays on.** Screenshots of the app are blank, so design
   evidence comes from Roborazzi renders in CI, not emulator screenshots.
10. **Surfaces drawn by the system.** Gboard draws the inline chips, so only
    the title, subtitle and icon are ours: the icon becomes the atom mark. The
    Autofill dropdown gets a small branded `RemoteViews` layout (paper, ink
    text, mono subtitle).
11. **App icon:** the atom mark on an ink rounded square, as an adaptive
    vector icon.

### 8.4 Token architecture (Compose)

All in `ui/theme/`; one decision, one place.

| File | Contents |
|---|---|
| `AtomicPalette.kt` | Immutable `AtomicPalette` (raw tokens from §14: ink, paper, white, surface, raised, signal, signalHover, signalDeep, signalLight, signalMist, textBody, slate, line, track, inkDeep, error, onError, errorContainer, onErrorContainer) plus **role** names screens use: `background`, `card`, `panel`, `module` (ink), `textPrimary`, `textSecondary`, `accent`, `accentOnModule`, `borderControl`, `borderQuiet`, `focus`. `LightPalette`, `DarkPalette` (§13.9) |
| `AtomicTheme.kt` | `LocalAtomicPalette = staticCompositionLocalOf`, `AtomicTheme.colors / .type / .motion`; maps to an M3 `ColorScheme` per §14.3 so stock components (Snackbar, menus, pickers) never fall back to purple. A theme change recomposes from the root once; no global mutable state |
| `AtomicType.kt` | `FontFamily` for display, body and mono from `res/font`; styles `displayXL 48`, `displayL 40` (top-level title), `displayM 30` (pushed title), `displayS 22` (row and card titles), `buttonLabel 20`, `bodyLead 18`, `body 16`, `bodySmall 15`, `secret 16 mono`, `monoLabel 13`, `monoCaption 12`, `counter 12`, with tracking; floor 12 sp |
| `AtomicDimens.kt` | `AtomicSpacing` (4…56), `AtomicRadius` (xs 3, sm 4, md 6, lg 8, sheet 28, pill), `AtomicBorder` (hair, rule, structure, control, selected, danger, priority), `AtomicSize` (touch 48, primary button 52, secondary 44, icon 20/24, tile 40, back 36, nav pill 104×44, chip 32, top bar 56) |
| `AtomicElevation.kt` | Hard shadow offsets 2/3/4/5/6/8 and `Modifier.hardShadow(offset, color, shape)` drawn with `drawBehind` (no blur, cheap); `elevation = 0` everywhere |
| `AtomicMotion.kt` | `press 120`, `state 150`, `toggle 200`, `enter 350`, `reveal 500`; `Ease = CubicBezierEasing(0.25, 0.1, 0.25, 1)`; `LocalReducedMotion` from `Settings.Global.ANIMATOR_DURATION_SCALE == 0` → fades only, no translate or rotate |
| `AtomicBreakpoints.kt` | Width classes: compact < 600 dp, medium 600–839, expanded ≥ 840 |

Migration: during 7.2–7.6 the old `AtomicColors.X` names stay as deprecated
shims that read the new palette, so screens move one at a time and CI stays
green. 7.9 deletes them.

### 8.5 Components (refactor first, new only where nothing exists)

| Layer | Component | From |
|---|---|---|
| Foundation | `AtomicText` (display and mono uppercase with semantics kept), `AtomicIcon` (Outlined only), `Modifier.hardShadow`, `Modifier.atomicFocusRing` (2 dp Signal, 2 dp offset), `AtomMark` (vector) | new |
| Atoms | `AtomicButton(variant)`: Primary, Solid, Ghost, GhostOnModule, LightOnSignal, Destructive, Text; press sinks 2 dp | refactors `AtomicPrimaryButton`, `AtomicOutlinedButton`, `AtomicDestructiveButton` |
| | `AtomicIconButton`: Back (ink square), Action (Signal), Toolbar (ink + shadow), Danger (error) | new |
| | `AtomicTextField`: 2 dp ink border, mono label above, error helper below, `secret` mode (mono, real case, reveal toggle) | refactor |
| | `AtomicChip` (filter), `AtomicStatusPill` (`ON`/`ARMED`/`OFF`), `AtomicTag` (priority, status), `AtomicBadge`, `UnreadDot` | `FilterChipPill`, `IssueBadge`, `StatusDot` |
| | `AtomicSwitch` (spec off state: paper track, ink outline), `AtomicCheckbox`, `AtomicStepper`, `AtomicSegmented` | refactor + new |
| | `AtomicRule` (ink) / `AtomicHairline` (line); `AtomicBar` (value bar and 2 dp indeterminate bar) | `EntropyMeter` becomes a `AtomicBar` use |
| Molecules | `AtomicSectionHeader` (mono label + optional pill or text action + ink rule), `AtomicTitleRow` (display title + mono counter + rule) | `SectionLabel` |
| | `AtomicSettingsRow` (display title, Signal "→"), `VaultItemRow` (icon tile, title, mono username, tags, quick-copy), `AtomicFactSheet`, `AtomicStatTile`, `AtomicCodeWell` | `IconTile` + new |
| | `AtomicEmptyState`, `AtomicLoadingState`, `AtomicErrorState`, `AtomicWarningBox`, `AtomicCallout` | new (8.8) |
| Organisms | `AtomicTopBar` (home header / pushed header, 1 dp ink rule), `AtomicNavigation` (bottom bar with one ink pill; `NavigationRail` at medium and up), `AtomicFabStack` | `AtomicTopBar`, `AtomicBottomNav` |
| | `AtomicSheet` (paper, 28 dp tops, mono label + rule, full-width primary) and `AtomicConfirmSheet` (destructive, states target and numbers) | replaces `AtomicDialog` / `AtomicDialogPanel` (keeps D8: still one overlay type) |
| | `VaultHealthCard` (ink module), `VaultSessionCard`, `AtomicDangerZone`, `ItemEditorScaffold` | new |
| Removed | `LiquidGlassSurface`, `AmbientVaultBackground`, `GlassSpring`, `GlassEasing`, glass and alias colour tokens, `AtomicFontSize`, `AtomicFontWeight` | 7.9 |

Every component gets a catalogue snapshot: default, pressed, focused,
disabled, selected and error; light and dark; font scale 1.0 and 2.0.

### 8.6 Navigation and information architecture

- **Top level stays four destinations**, because each is a real job: `VAULT`,
  `GENERATE`, `HEALTH` (was "Audit": it names what the user gets),
  `SETTINGS`. Bottom bar per spec: paper, 1 dp ink top rule, the active
  destination as an ink pill with icon and mono label, the others icon-only
  with content descriptions.
- **Pushed screens**: ink back square + display title on one line, 1 dp ink
  rule. System back and predictive back behave the same.
- **Add**: the Home add menu becomes the spec's FAB stack: `+ NEW LOGIN`
  (ink) with `CARD` and `IDENTITY` above it.
- **View before edit (new)**: tapping an item opens an **item detail** screen
  (copy buttons, reveal, live 2FA code, fill receipts, change-password link).
  `EDIT` opens the editor. This removes accidental edits and is how people use
  a vault: read and copy far more often than change.
- **One editor scaffold** for login, card and identity: shared header,
  sections, `SAVE LOGIN`/`SAVE CARD` pinned full width, delete as a toolbar
  danger icon with a confirm sheet, and an unsaved-changes sheet on back.
- **Settings** keeps its routes; Backup and Privacy proof stay pushed screens,
  and the timeline stays under Privacy proof.
- **Wide screens**: medium and expanded use a `NavigationRail`, and Vault
  becomes list + detail side by side.
- **Outside the app** (Autofill unlock, save, phishing warning) stay separate
  activities, which Android requires, but use the same theme and components.

### 8.7 Screen by screen

Each screen also has the states in 8.8.

**Onboarding (create vault)**
- Purpose: set a master password the user will remember. Primary: `CREATE VAULT`. Secondary: turn on fingerprint.
- Layout: eyebrow `NEW VAULT · STEP 1 OF 2` → split headline "One password. **Only you know it.**" → master password + confirm (secret fields) → strength bar with a word → fact line `ARGON2ID · HARDWARE KEYSTORE · NO NETWORK` → full-width Primary. Step 2: fingerprint on/off with a plain explanation, `FINISH`.
- Notes: says plainly that a forgotten master password cannot be recovered. Errors explain the fix ("Passwords don't match. Type the second one again.").

**Unlock**
- Purpose: open the vault. Primary: `UNLOCK`. Secondary: `USE FINGERPRINT`.
- Layout: atom mark → eyebrow `VAULT / LOCKED` → display "AtomicVault" → master password field (keeps the test tags) → Primary → Ghost fingerprint button → mono line `AUTO-LOCK · ON LEAVING THE APP`.
- States: `UNLOCKING…` with an ink bar (Argon2 takes about a second); wrong password: error text and a mono attempt count; key store unavailable: warning box with the fix.

**Vault (home)**
- Purpose: find an item and copy from it. Primary: search. Secondary: add, filter, lock.
- Layout: home header (atom tile + mono `ATOMIC` over display `VAULT`, Signal `LOCK NOW` icon button) → title row `VAULT` with counter `42 ITEMS` → search field → filter chips `ALL · LOGINS · CARDS · IDENTITIES · NEEDS ATTENTION` (+ tag chips) → `VaultItemRow` list (white rows, 1 dp line border) → FAB stack.
- States: first use (empty, says what to add first and that Autofill can save logins for you); no search results (says what was searched, offers to clear filters); a damaged item shows a warning tag, not a crash.
- Wide: list + detail.

**Item detail (new)**
- Purpose: copy or fill a credential safely. Primary: `COPY PASSWORD`. Secondary: copy username, reveal, open site, `EDIT`.
- Layout: pushed header with the item title → fact sheet (username, password in mono with reveal, website, app) → 2FA code row (mono, countdown bar) → fill receipts line → tags → `CHANGE PASSWORD ON GITHUB.COM →` text action when a check flags it.
- Notes: copies say what happens next ("Copied. Clears from the clipboard in 30 s.").

**Item editor (login, card, identity)**
- Purpose: create or change an item. Primary: `SAVE LOGIN` / `SAVE CARD` / `SAVE IDENTITY`.
- Layout: `ItemEditorScaffold`: sections with mono headers (`LOGIN`, `WEBSITE OR APP`, `2FA`, `NOTES`, `ORGANISE`, `CUSTOM FIELDS`) → generator inline under the password (`GENERATE` text action opens the generator sheet) → pinned Primary. Delete: toolbar danger icon → confirm sheet "Delete 'GitHub'? This removes it from this phone. Backups you already made keep it."
- Card: number in mono groups of four, expiry `MM / YY`, masked CVV. Identity: Aadhaar and PAN with masking and validation messages (IndianIds).

**Generate**
- Purpose: make a strong password. Primary: `COPY PASSWORD`. Secondary: `NEW PASSWORD`.
- Layout: white card with a 5 dp ink shadow holding the password in mono (wraps, real case) → strength bar + word → stepper `LENGTH 20` → segmented toggle `PASSWORD · PASSPHRASE` (passphrase only if implemented; otherwise omitted, not stubbed) → character-set switches as settings rows → note "Skips O, 0, I, l and 1".

**Health (was Audit)**
- Purpose: know what to fix first. Primary: the top finding's action.
- Layout: `VaultHealthCard` (ink module: `HEALTH` in `signal-light`, display `92 / 100`, bar, "3 logins reuse a password") → stat tiles `REUSED 2 · WEAK 1 · EMPTY 0 · TOTAL 42` → section `NEEDS ATTENTION · 3` → finding cards with priority left bars and a `CHANGE PASSWORD →` action → device integrity fact sheet → `RE-SCAN` Ghost.
- States: all clear (says what was checked, with numbers); scanning (`CHECKING 42 ITEMS…` + bar).

**Settings**
- Purpose: change how the vault behaves. Groups with mono headers and ink rules:
  - `SECURITY`: auto-lock (segmented); fingerprint, as an "on" card with an `ARMED` pill
  - `AUTOFILL`: status card with an `ON`/`OFF` pill and `OPEN ANDROID SETTINGS →` when off
  - `APPEARANCE`: Light / Dark / Match system, segmented
  - `ORGANISE`: folders and tags as rows with counts
  - `DATA`: `BACKUP & RESTORE →`, `PRIVACY PROOF →`
  - `ABOUT`: version and signing certificate
- Split into one file per group (the current file is 650 lines).

**Backup and restore**
- Purpose: export a backup you can restore; restore safely. Primary: `EXPORT BACKUP`.
- Layout: section `EXPORT`: passphrase + confirm → Primary → fact line with the last export date (ISO). Section `CHECK`: `CHECK BACKUP` Ghost (decrypts and counts, changes nothing). `AtomicDangerZone` `RESTORE`: warning sentence → `REPLACE VAULT` destructive → confirm sheet with numbers ("Replace 42 items with 37 from the backup made 2026-09-30 12:01? You can undo until you leave this screen.").
- Success: "Exported 42 items." / "Restored 37 items." + `UNDO RESTORE`.

**Privacy proof**
- Purpose: verify the app's claims. Layout: hero `CHECKS PASSED 9 / 9` → one fact-sheet row per claim with an `ON`/`FAIL` pill and how it was checked → verify block: APK signing SHA-256 and ledger head hash in code wells → `SECURITY TIMELINE →`.

**Security timeline**
- Purpose: see what happened, when. Layout: rows with an ISO mono timestamp, body event, source tag, result tag (failure in `error` with the word). Filter chips `ALL · FAILURES · FILLS`. Empty: "Nothing recorded yet. Unlocks, fills and backups appear here."

**Autofill unlock (VaultAuthActivity, outside the app)**
- Purpose: authenticate to fill. Layout: eyebrow `FILL · GITHUB.COM` → display "Fill with AtomicVault" → master password field + `UNLOCK AND FILL` Primary; the biometric prompt opens on top when armed. Wrong-site cases go to the phishing warning instead.

**Save prompt (AutofillSaveActivity)**
- Purpose: confirm a save. Layout: sheet style: `SAVE LOGIN` label → fact sheet (site, username, masked password) → `SAVE TO VAULT` Primary, `NOT NOW` Ghost; update case says "Updates the password for ashu@… on github.com."

**Phishing warning**
- Purpose: stop a fill on a look-alike site. Loud on purpose: error-bordered module, display "This is not github.com", the reason in plain words ("swaps similar-looking letters"), the two domains side by side in mono, `GO BACK` Solid as the safe default, `FILL ANYWAY` as a text action only.

**One-time notices and sheets** (keyboard removed, restore undo, unsaved changes): `AtomicSheet` with a mono label, a two-line headline, body text and one Solid `GOT IT`.

### 8.8 States catalogue

| State | Component | Rule |
|---|---|---|
| Initial and loading | `AtomicLoadingState` | Mono caps with an ellipsis (`UNLOCKING…`, `CHECKING 42 ITEMS…`); a 2 dp ink bar after 400 ms; skeleton rows only for the vault list on first unlock |
| Empty | `AtomicEmptyState` | Surface panel: one sentence, the next step as a button |
| Error | `AtomicErrorState` / inline | What happened, how to fix it, optional detail (an error code in mono) |
| Partial data | Warning tag on the row + `AtomicWarningBox` in the item | "1 field could not be decrypted. The rest is safe to use." (from `FieldOpener`) |
| Offline | n/a | The app has no network permission. Instead of an offline state, the Home storage tile says `CLOUD: NONE` |
| Success | Inline confirmation | Real numbers, no toasts with only "Done" |
| First use | Empty states with a first action + the onboarding flow | — |
| Selected | 2 dp Signal border | Only if multi-select is added later; not added for its own sake |

### 8.9 Responsive

| Width | Layout |
|---|---|
| Compact < 600 dp (phones) | Single column, bottom bar, FAB stack |
| Medium 600–839 (large phones in landscape, small tablets, unfolded) | Navigation rail; Vault list + detail; content max 560 dp centred for forms |
| Expanded ≥ 840 (tablets) | Rail; list (360 dp) + detail; Health with stat tiles in a row and findings beside the health card |
| Short height (landscape phone) | Header collapses to the pushed style; sheets become scrollable; pinned buttons stay reachable |

### 8.10 Motion

| Event | Motion |
|---|---|
| Button press | Sinks 2 dp, shadow to 0 over 120 ms |
| Chip, switch, pill state | 150–200 ms colour change |
| Sheet in | Slides up 16 dp + fade, 350 ms |
| Screen change | 350 ms fade with an 8 dp shift (no slide-in-from-the-side carousel) |
| Bars (strength, health, 2FA countdown) | Width over 350 ms, linear for the countdown |
| Unlock success | One 150 ms fade to Home; nothing celebratory |
| Reduced motion (system animator scale 0) | Fades only, at most 150 ms; countdown jumps per second |

No springs, bounces, ambient loops or decorative motion.

### 8.11 Accessibility

- A unit test computes WCAG ratios for every text-on-background pair the
  palettes allow (light and dark) and fails below 4.5:1 (3:1 for display
  ≥ 24 sp). Signal on ink and orange on paper are not expressible.
- Touch targets at least 48 dp (`minimumInteractiveComponentSize`), including
  chips and ✕ buttons.
- 2 dp Signal focus ring for keyboard and D-pad focus.
- Snapshot set at font scale 2.0: titles may wrap to two lines, nothing
  clips.
- TalkBack: every icon-only button has a label; status pills read
  "Autofill: on"; secrets say "Password, hidden" until revealed.
- Colour never alone: strength and health always carry a word; failures carry
  `FAIL`.
- Reduced motion as in 8.10.

### 8.12 Performance

- Theme via a static `CompositionLocal`, not ~50 global observable states, so
  a theme change no longer touches every reader individually.
- Hard shadows are a single `drawBehind` rect: no blur, no offscreen layer.
- No ambient background animation (`AmbientVaultBackground` removed).
- Fonts are local; variable files keep it to three font files.
- Lists use `LazyColumn` with stable keys and `contentType`; row composables
  take immutable models.
- Baseline profile (Phase 4) regenerated after the rebuild.

### 8.13 Steps, each its own commit and CI run

Design reference: the 18-board canvas at
https://claude.ai/artifact/KXY28fKQRhZcqpL4cgaGya (every screen in 8.7, the
first-use state, restore confirm sheet, dark variant, tokens and components,
tablet list + detail). Where the canvas and this section differ, this
section wins.

Learned in 7.1: Bebas Neue only has capital letterforms, so Display text is
uppercase on screen while the string (and what TalkBack reads) keeps its
normal case. No uppercase transform is needed for Display; mono labels use
`AtomicType.caps`.

| Step | Content | Evidence (CI) |
|---|---|---|
| 7.0 | This plan; the spec in `docs/design/`; D15 | — |
| 7.1 | Test safety first: emulator script and `RegressionUiTest` find nodes by test tag or case-insensitively. Then fonts (`res/font`, licences), palettes, `AtomicTheme`, type, dimens, elevation, motion, reduced motion, `AtomicText`, compatibility shims, adaptive app icon | Unit: contrast test, token sanity; snapshot: token sheet light and dark |
| 7.2 | Components from 8.5 | Component catalogue snapshots (states × themes × font scales) |
| 7.3 | Shell: top bars, navigation (bottom bar and rail), FAB stack, `AtomicSheet` replaces `AtomicDialog` | Snapshots; emulator walk |
| 7.4 | Unlock, onboarding, and the three Autofill activities + branded dropdown layout | Emulator: create, restart, unlock, Autofill tap (step after run #33) |
| 7.5 | Vault home, **item detail (new)**, editor scaffold for all three types | `RegressionUiTest` additions: open item → copy → edit → save; editor keeps TOTP and package (existing) |
| 7.6 | Generate, Health, Settings (split), Backup, Privacy proof, Timeline | Snapshots per screen and state |
| 7.7 | States and UX pass: every screen through 8.8; copy pass against §12 | Snapshot of each empty, loading and error state |
| 7.8 | Responsive and accessibility pass: compact, medium and expanded renders; font scale 2.0; semantics tests | Snapshots at 360, 600 and 840 dp; semantics assertions |
| 7.9 | Clean-up: delete Liquid Glass, shims, alias tokens, old type objects; a CI check fails on `Color(0x` outside `ui/theme` and on raw `.dp` literals outside `ui/theme` and `ui/components`; spec §15 checklist | Grep check in CI; final snapshot review |
| 7.10 | Report: what was extracted, architecture, components, screens, UX changes, responsive, accessibility, performance, removed, remaining | `docs/design/IMPLEMENTATION-REPORT.md` |

Done when: every screen in 8.7 is rebuilt on the components; no Liquid Glass
code remains; the 7.9 check passes; CI is green; and the phone test covers the
new look (appearance section added to the checklist).

### 8.14 Risks

| Risk | Containment |
|---|---|
| Tests break because visible text becomes uppercase | 7.1 moves them to tags first, before any visible change |
| A big visual change hides a functional regression | The UI layer only; view models untouched; every step needs CI green, including the emulator flow |
| Display font lacks a script (Hindi, Cyrillic) | User text never uses Display; system fallback for anything else |
| Uppercase misread by screen readers | `AtomicText` keeps a normal-case semantics label |
| Users who liked dark lose it | Dark variant from the spec, kept as their saved choice |
| APK size | About 0.5 MB of fonts; R8 removes unused icons |
