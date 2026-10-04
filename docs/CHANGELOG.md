# Change log

## Branch `claude/atomic-vault-analysis-1fj2ok` (continues `fix/biometric-ime-autofill`)

Work from [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md), newest first. CI
results are recorded in the plan's progress table.

### Phase 6: India-ready identity fields

- The identity editor takes an **Aadhaar number** (checked with UIDAI's Verhoeff
  check digit, stored as a sensitive field) and a **PAN** (format-checked).
  `IndianIds` also validates IFSC codes and UPI IDs for later item types.
- A custom field labelled **UPI PIN, MPIN, ATM PIN or OTP** shows a warning:
  these should never be stored, even encrypted.
- Saving an identity no longer drops custom fields it does not show (e.g. from
  a backup).

### Phase 6: fill receipts

- The login editor shows **where and when a login was filled**: "Filled 3 times
  · last 2 Oct, 14:05", and warns if a fill ever went to a site or app other than
  the login's own. Built on the Trust Ledger, which stores the item and target
  only as hashes. Fills picked from the locked-vault chip are recorded from
  Android's fill event history (`FillReceipts`), each exactly once.

### Phase 6 (first differentiator): phishing guard

- **Look-alike site warning.** When a page has no saved login but resembles a
  site the user has one for, AtomicVault says so instead of staying silent:
  a "Not github.com" chip in the keyboard strip (vault open) or a warning screen
  after unlocking (vault locked). It never fills on a look-alike.
- `PhishingGuard` (pure Kotlin, offline, only the user's own saved domains):
  digit/letter swaps (`paypa1.com`, `g00gle.com`, `rnicrosoft.com`), one or two
  typos (`githuh.com`, `hdfcbnak.com`), the brand inside another site
  (`github-login.com`, `github.com.account-verify.xyz`) and punycode homoglyphs
  (`gіthub.com` with a Cyrillic і). Subdomains of saved sites and the same name
  on another country domain (`amazon.in` vs `amazon.com`) never warn.
- Each warning is recorded in the Trust Ledger ("Look-alike site warning").

### Phase 4: performance, and 2FA codes

- **Search no longer decrypts the whole vault per keystroke (P1).** Usernames
  are decrypted once per item version and cached for the unlocked session; tags
  load in one query instead of one per row.
- **Backup export and the security scan read the vault in three queries** instead
  of four per item.
- **Moshi uses its generated adapters only (P6)**; the reflection factory (and
  `kotlin-reflect`) is gone. `BackupRoundTripTest` checks every field of every
  item type survives export and import.
- **The dashboard keeps no decrypted secrets (P5)**: findings hold id, title and
  type only.
- **2FA codes (TOTP, RFC 6238)** computed on the device: an "Authenticator key"
  field in the login editor shows the live code with a countdown and Copy, and
  Autofill fills the code on one-time-code fields (SMS-style 2FA screens are
  left to the keyboard). `TotpTest` uses the RFC's own test vectors.

### Phase 3: Autofill built for Gboard and every keyboard

- **Suggestions in the keyboard strip.** On Android 11+ every AtomicVault
  suggestion carries an inline chip (Gboard, Samsung, SwiftKey...) as well as
  the classic dropdown. Long-pressing a chip opens AtomicVault; it used to start
  the fill itself (B10).
- **Vault locked:** one "Unlock AtomicVault" chip. Tapping it authenticates once
  and returns this screen's accounts, ready to fill (F1). It used to show
  nothing unless a fingerprint had been used in the last 30 seconds.
- **Vault open in the app:** one chip per matching account with its username;
  tapping asks for the fingerprint or master password, then fills username and
  password together.
- **Master password everywhere (F3).** A new `VaultAuthActivity` tries the
  Keystore-bound fingerprint first and always offers the master password, so
  Autofill works on phones without biometrics. One `VaultUnlocker` does password
  unlock for the app and Autofill alike.
- **Saving always works (F2).** "Save to AtomicVault" opens a short confirm
  screen that authenticates and saves (`AutofillSaveActivity`); the captured
  login waits in memory only (`PendingSaves`, 5-minute expiry), never on disk.
  Two-page logins (email, then password) are saved together (`FLAG_DELAY_SAVE`).
- **Sign-up forms** get a "Strong password" chip that fills a generated password
  into the new and confirm fields; the save prompt then stores it.
- **Field detection rewritten (B11)** as `FormClassifier` (pure Kotlin, tested):
  autofill hints > HTML autocomplete/type > input type > whole words. No more
  "pass" matching "passport", hidden or disabled fields are never filled,
  labels are not fields, postal/promo codes are not 2FA codes.
- **No key without a screen.** The 30-second grace key and
  `tryRevealWithoutPrompt` are deleted (purged on upgrade); the biometric
  unlock key is kept, so nobody has to re-enable biometrics.
- Autofill never acts on AtomicVault's own screens (B7, service side).
- Settings shows whether AtomicVault is the active Autofill service (live), with
  the Chrome step; the "Arm autofill" switch, which only toggled biometrics, is
  gone.

### Phase 2: data safety and crash fixes

- **Lock never corrupts a save (B4).** New `VaultSession` owns the open vault
  (key, connection, repository) for the whole process. Locking stops new work
  at once and closes / zeroes the key when the last running operation ends;
  before, locking mid-save sealed the item under a zero-filled key.
  `VaultSessionTest` locks during a running save.
- **No more silent key-store fallback (B5).** `VaultMetaStore` picks its store
  once per install and remembers it; if that store cannot open later the app
  says so instead of showing onboarding over the real vault. Creating a vault
  next to an orphaned database renames the old file aside instead of failing.
  The envelope is written synchronously.
- **Backups and device transfer exclude everything** (`data_extraction_rules`,
  `backup_rules`); `allowBackup="false"` alone does not stop device-to-device
  transfer on Android 12+.
- **Editing a login no longer wipes its TOTP secret or Android app (B6)**; the
  app wipe was found while fixing the TOTP one.
- **KDF parameters stored with a vault are used at unlock (B8)**, validated.
- **Trust Ledger (B9):** an explicit sequence number, one transaction for
  "read head + append", one cached connection, and a migration that keeps
  existing chains valid. Same-millisecond events, concurrent writers and a
  clock moved backwards no longer report "chain broken".
- **Background vault work cannot crash the app (F4)**; failures are shown.
- **Search** waits for a pause in typing and a newer query cancels an older one,
  so results never arrive out of order (F5).
- **Security score** counts logins only; cards and identities were all flagged
  as "empty password" (F6). The analysis runs off the main thread.
- **Restore is all-or-nothing (F7)**, keeps this phone's biometric setting,
  and can be undone until the vault locks (the previous vault is kept in
  memory only) (F8).
- **A damaged field no longer breaks the security scan or backups (F9)**: it
  reads as empty and the item is flagged `damaged`.

### Phase 1: Atomic keyboard removed

- **Removed** the input method service, its reveal activity/coordinator, the
  in-app `LiquidGlassKeyboard`, `res/xml/method.xml`, the manifest entries, the
  R8 keep rule, the Settings section and the keyboard snapshot tests. Filling
  now goes only through Android Autofill, which shows inside Gboard (or any
  keyboard) on Android 11+.
- **Unlock lockout fixed (B1).** The unlock screen forced the in-app keyboard,
  which had no accented letters, non-Latin scripts or emoji, while onboarding
  used the system keyboard. Unlock now uses a password field on the system
  keyboard. Regression test: `RegressionUiTest` types `Grüße-é-नमस्ते-🔐-9`.
- **Password normalization (B2).** New vaults and backups derive from the NFC
  form; unlock and import try NFC, then the raw input, and a vault that only
  opened with the raw form is re-wrapped once. `MasterPassword`,
  `MasterPasswordTest`.
- **Screenshots blocked on every screen (B3)**, including onboarding.
- **Autofill kept off the app's own fields (B7, app side)**: nothing offers to
  save the master password or an item being edited.
- **Privacy Proof made honest**: screen protection is read from the live window
  (it always said "not yet enabled"), and a new live check confirms the app
  registers no keyboard service. `ManifestPrivacyClaimsTest` enforces it.
- Unlock errors other than a wrong password now say what failed (F10, partly).
- One-time notice for users upgrading from a version with the keyboard.
- CI: the emulator script (`emulator_check.sh`) fails if any input method is
  registered, and now restarts the app and unlocks through the system keyboard.

### Phase 0: baseline

- Failing unit tests print full stack traces in the CI log.
- `VaultEnvelopeFixtureTest`: vault envelopes generated independently in
  Python guard key derivation for existing users.
- `AUDIT-REPORT.md` marked superseded. `fix/v0.2-audit-and-bugfixes` was
  checked: its three commits are already contained in the current code and
  the branch can be deleted.

## Branch `fix/biometric-ime-autofill`

Everything below is on the branch `fix/biometric-ime-autofill`, 21 commits from
2026-09-18 to 2026-09-19, on top of `main` at `2217d9a` ("Update-c003", the
commit that carried the earlier audit's edits). The branch is **not merged**.
Against `main` it changes 42 files (+4395 / -2207).

Every commit on the branch passed the `Android CI` workflow (unit tests, lint,
debug build, and from `49aa60a` onward the emulator job), except where a commit
is noted as fixing the previous one's compile error.

Nothing in this list has been verified on a physical phone yet. See
[PHONE-TEST-CHECKLIST.md](PHONE-TEST-CHECKLIST.md).

## Upgrade notes (read before releasing)

- **Biometric unlock must be turned on again once** after upgrading from 0.2.1.
  The Keystore key layout changed (see the biometric entry below). The old key
  is purged automatically; the master password keeps working throughout and no
  vault data is touched.
- The auto-lock setting changed meaning in the UI only. The stored value `0`
  always meant "lock immediately"; the chip that used to be labelled "Never"
  now says "Immediately". Nobody's actual behaviour changes.
- **The Atomic keyboard is removed.** Anyone who had it selected falls back to
  the system default keyboard automatically; a one-time notice explains the
  change and offers to turn on Autofill.
- Version is still `0.2.1` / `versionCode 2`. It needs bumping before a release
  build can update an installed 0.2.1.

## Security and data-safety fixes

| Area | Problem | Fix | Commit |
|---|---|---|---|
| Biometric unlock | One 30-second timed Keystore key backed the `BiometricPrompt` `CryptoObject` flow. `Cipher.init` on a timed key throws outside the window, so unlock silently did nothing on a cold start and enabling biometrics could crash. | Two keys: a per-use **unlock** key for the `CryptoObject` flow, and a timed **grace** key used only for the no-UI metadata matching in Autofill and the keyboard. Legacy single-key state is purged. | `e885cad` |
| Biometric prompt | A wrong-finger attempt was reported as a terminal error, ending the caller's flow while the prompt was still open. A second tap could stack a second prompt. A missing/invalidated key did nothing. | Wrong finger is a non-terminal callback. One prompt at a time. A missing key resets the UI and shows a message. Settings shows the Keystore state, not the stored setting. | `e885cad` |
| Autofill service | The DEK was not zeroed on the fill path's early exits; a parse failure never completed the callback; the cancellation signal was ignored. | Parse inside the `try`, zero in `finally`, honour cancellation. | `e885cad` |
| Autofill matching | Every site in a browser shares the browser's package, and a package match was treated as strong evidence, so a login saved from Chrome was offered on every other website in Chrome. | A package match only counts for native-app screens (no web domain). For web content the domain decides. An item that stores a web domain is never revealed by package alone (the keyboard cannot see domains). | `6c6309a`, `9631167` |
| Autofill save | The same match let a save on site B find site A's item and overwrite it. The update was a full replace carrying only username and password, wiping the item's notes, TOTP secret, custom fields, tags and folder. | `AutofillSave`: only an item that already holds the same username is updated (two accounts = two items); everything else is carried over; the browser package is no longer stored for web logins. | `6c6309a` |
| Keyboard reveal | A revealed credential was typed into whatever field was focused when the biometric screen returned. | Typed only into the field it was requested for (package, field id, input type). If it arrives while no input is attached it waits up to 15 s for the same field. The wait has a 60 s timeout. | `9631167` |
| Keyboard reveal | The completed request stayed referenced from a static, keeping the revealed password reachable. | The coordinator drops the request as soon as it completes. | `9631167` |
| Clipboard | The 45 s clear compared the clipboard to the copied value. From Android 10 a backgrounded app reads nothing from the clipboard, so in the normal flow (copy here, paste elsewhere) the clear never happened. | Tracks whether anything else replaced the clip via change notifications and clears without reading. Locking the vault also clears. | `9631167` |
| Payment card / identity editors | Edits opened blank (async load) and saving overwrote the stored item; saving dropped the item's tags; there was no way to delete an item. | Editors wait for the load; tags are preserved; a Delete action with confirmation. | `e885cad`, `b75ed5e` |
| Auto-lock | The "Never" chip stored `0`, which the lifecycle observer treats as "lock immediately". | Chips are Immediately / 1 / 5 / 15 min and the label reads "Lock after leaving the app". | `b75ed5e` |

## Keyboard (system input method)

- Attach the view-tree lifecycle/saved-state owners to the IME window's decor
  view, not only the `ComposeView` (likely start-up crash) and resume the
  lifecycle on each input start. `e885cad`
- Enter runs the field's own IME action (search/send/go/next/done) or types a
  real newline; backspace uses key events so a selection deletes as a whole and
  surrogate pairs are not split; shift is one-shot; a second symbols page adds
  `_ = / \` and similar. `e885cad`
- Suggestion lookup (open the vault DB and match) moved off the main thread.
  The service scope is cancelled on destroy. `9631167`
- Restyled to the design reference: flat 8 dp keys, a white primary Enter key
  whose icon follows the field's action, a compact "Atomic Shield" strip on
  password fields only. `7dfae78`

## User interface (design reference)

The whole UI was moved to `atomicvault_design_system_reference/`. Only its
visual language was taken: flat `#131313` canvas, opaque stepped surfaces,
emerald `#4EDEA3` accent, white primary button, 8/12/16/24 dp radii, uppercase
micro labels. Its mock-only content (defence index, "air-gapped core", hardware
attestation claims, passkeys, emergency access, keylogger simulator) was
deliberately not built. See [DECISIONS.md](DECISIONS.md).

| Change | Commit |
|---|---|
| Palette from the reference screenshots; `LiquidGlassSurface` made flat (removed the specular line and radial glow on every card); 12 dp buttons/fields, emerald switch, tinted badges | `7dfae78` |
| Home: one card per entry with a letter or type icon; icon buttons; no dead space | `e8cf82e` |
| Material dialogs were pill-shaped blobs (`Shapes.extraLarge` was 999 dp) and used Material's lilac surface tiers; all surface tiers now come from the tokens | `b59af85` |
| `AtomicTopBar` and floating four-tab bottom navigation (Vault, Generate, Audit, Settings) | `235c038` |
| One `AtomicDialog` (24 dp sheet, stacked full-width buttons, optional content slot) replacing every `AlertDialog`; Unlock screen | `cd30519` |
| Onboarding, Security dashboard (single scrolling page, score ring, 2x2 stats, finding cards), credential editor grouped in cards, header on every pushed screen | `55f156d` |
| Password generator (output card, length slider 8-128, option card) | `ddeae4d` |
| 360 dp / 1.5x font fixes: auto-lock chips wrapped mid-word, placeholders wrapped inside single-line fields, button labels centre when they wrap | `ea1d3a1` |
| Privacy proof and timeline rows wrap instead of overflowing | `517cf5b`, `e00c67d` |
| Home `+` opens Login / Payment card / Identity (they were only in Settings) | `b75ed5e` |
| Settings footer showed a hard-coded `v1.0.0`; now reads `BuildConfig.VERSION_NAME` | `235c038` |

## Accessibility

- `AtomicSwitch` is `toggleable(role = Switch)` so screen readers announce an
  on/off switch (it was a bare `clickable`), with a 48 dp touch target.
- Filter chips get a 48 dp touch target and expose their selected state;
  bottom-nav tabs are `selectable(role = Tab)`. `ca839df`

## Tests and CI

- `android-ci.yml`: runs on any non-`main` branch and pull requests. Unit tests,
  lint, debug APK, UI snapshot renders, reports; no signing secrets. `e885cad`
- `UiSnapshotTest`, `UiStressSnapshotTest` (360 dp wide, 1.5x font, dialogs)
  render the real screens to PNGs, uploaded as `AtomicVault-UI-Snapshots-<n>`.
- Emulator job (API 29): installs the debug APK, selects the Atomic keyboard,
  creates a vault by typing a master password, walks the four tabs and the Home
  `+` menu, and fails on any logged crash. `49aa60a` to `f8d0a74`
- Regression tests: `RegressionUiTest` (auto-lock chips, tag preservation,
  delete flow, add menu, switch/chip semantics, dialog reachability),
  `VaultLifecycleObserverTest` (0 = immediately), `CredentialMatcherTest` (incl.
  look-alike domains), `AutofillSaveTest`, `ClipboardHelperTest`.
- `docs/PHONE-TEST-CHECKLIST.md`. `14e2f66`

## Files added

- `app/src/main/java/com/example/ui/components/AtomicChrome.kt` (top bar,
  bottom nav, icon tile, status dot)
- `app/src/main/java/com/example/autofill/AutofillSave.kt`
- `.github/workflows/android-ci.yml`, `.github/scripts/emulator_ime_check.sh`
- Tests listed above, under `app/src/test` and `app/src/testDebug`
- `docs/`

## Earlier audit (already on `main` before this branch)

`AUDIT-REPORT.md` describes the edits in `2217d9a` (autofill work off the main
thread, suspend `getItem`/`getAllCredentialsForSecurity`, fuller state clear on
lock). Its open findings are tracked in [TASKS.md](TASKS.md). Two claims in it
were wrong and are corrected above: DEK zeroing was *not* consistent (the fill
path's early exits skipped it), and the `remember { getItem }` to
`produceState` change broke the payment-card and identity editors.
