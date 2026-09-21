# Change log

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
