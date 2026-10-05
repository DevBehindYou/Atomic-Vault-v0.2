# Phone test checklist

What CI cannot check: it has no fingerprint sensor, no other apps, and runs
Android 10. Install the debug APK from the latest `Android CI` run
(artifact `AtomicVault-Debug-APK-<n>`); it installs beside the release app as
`com.atomicvault.android.debug`. Tick what you tested and note anything odd.

## 1. Biometric unlock (most important)

Biometric keys changed in this release, so anyone upgrading has to turn
biometric unlock back on once. The master password keeps working throughout.

- [ ] Fresh install, create a vault with **Biometric unlock** on. The prompt appears once and the vault opens.
- [ ] Lock the vault (lock icon), reopen: the fingerprint prompt appears on its own. Correct finger unlocks.
- [ ] Wrong finger: the prompt stays open and a later correct finger still works.
- [ ] Cancel the prompt: you land on the master-password screen with no error and no crash.
- [ ] Settings, turn Biometric unlock off then on. Works both ways.
- [ ] Add a fingerprint in Android settings, come back: the app says biometric unlock was reset and offers the master password (no crash, no data loss). Turn it on again.
- [ ] Tap the fingerprint row twice quickly: only one prompt.

## 2. Typing with your own keyboard (the Atomic keyboard was removed)

- [ ] Upgrade over a build that had the Atomic keyboard selected: the phone falls back to your normal keyboard, and after unlocking a one-time notice explains the change. "Later" never shows it again.
- [ ] Fresh install: no notice.
- [ ] Unlock with Gboard. Create a vault whose master password contains `é`, `ü` or a Hindi word, lock, unlock with the same password. It opens.
- [ ] On the unlock and onboarding fields Gboard shows no word suggestions and does not learn the password (incognito icon on Gboard).
- [ ] The app does not appear in Settings > Languages & input > On-screen keyboards.
- [ ] Screenshot on the onboarding screen is blocked (it was allowed before).

## 3. Autofill with Gboard (rebuilt; please test all of it)

Setup: Settings > Autofill card > "Turn on AtomicVault Autofill", pick AtomicVault. In Chrome: Settings > Autofill services > "Autofill using another service".

- [ ] Vault **locked**, open a login page in Chrome: Gboard's strip shows one **AtomicVault** chip. Tap it, use the fingerprint: the site's accounts appear as chips; tap one and both username and password fill.
- [ ] Same with the fingerprint cancelled: the master password works on that screen.
- [ ] Phone with **no fingerprint enrolled** (or biometrics off in AtomicVault): the same flow works with the master password.
- [ ] Vault **open** in the app (switch to Chrome within the auto-lock time): chips show each account's username; tapping asks for the fingerprint and fills.
- [ ] Long-press a chip: AtomicVault opens (it must not fill).
- [ ] A **sign-up** page: a "Strong password" chip fills both password fields; after signing up, "Save to AtomicVault" stores it.
- [ ] Sign in to a new site and tap **Save** on Android's prompt **hours after** last unlocking: the save screen asks to unlock and the login is saved (it used to be dropped).
- [ ] Google-style login (email page, then password page): one save prompt at the end stores both.
- [ ] Site A and site B in Chrome: B's page never offers A's login; saving on B never changes A's item.
- [ ] A native app login: saved, then offered in that app only.
- [ ] Nothing is offered inside AtomicVault itself (unlock, editor fields).
- [ ] Android 9 or 10 phone (if available): the same suggestions appear as a dropdown under the field.
- [ ] Phishing guard: with a saved github.com login and the vault open, visit a look-alike such as `githuh.com` or `github-login.com` (any page with a login form, or a local test page): the strip shows "Not github.com"; tapping it explains and fills nothing.
- [ ] Add an authenticator key to a login (Edit > Authenticator key, e.g. from a site's 2FA setup page): the 6-digit code and countdown show and match Google Authenticator. On that site's 2FA code screen, the AtomicVault chip fills the code.

## 4. Vault basics

- [ ] Home `+` offers Login, Payment card, Identity; each opens its own editor and saves.
- [ ] Edit a card that has a tag: the tag survives. Delete a card and an identity (new Delete button).
- [ ] Generator tab: length slider, toggles, Copy. Paste elsewhere; the clipboard clears after about 45 s.
- [ ] Audit tab: score, counts, a finding opens its item.
- [ ] Backup and restore: export, then import into a clean install; items, tags and folders come back.
- [ ] Restore tab, pick a backup, enter its passphrase, **Check backup**: it reports the item counts and date; nothing in the vault changes. A wrong passphrase reports an error.
- [ ] Restore over a vault with items, then tap **Undo restore**: the original items are back. Lock and unlock: undo is no longer offered.
- [ ] Edit a login that has a TOTP secret (imported from a backup) and save: the secret is still in the backup you export afterwards.
- [ ] Audit tab with a payment card and an identity in the vault: they are not counted as "empty password".
- [ ] Settings > Privacy proof: "Screen capture protection" is a pass and "No keyboard service" is a pass. The security timeline shows no "chain broken".

## 5. Auto-lock

Settings now reads **Lock after leaving the app**: Immediately, 1, 5 or 15 min.

- [ ] Immediately: leave the app, return, it asks to unlock.
- [ ] 1 min: return after 30 s (still open), return after 90 s (locked).
- [ ] The vault does not lock while you sit on a screen (there is no idle timer yet).

## 6. Layout

- [ ] System font size at the largest setting: Settings, editors, dialogs and the bottom bar stay readable, nothing overlaps or hides the Save/Confirm buttons.
- [ ] Landscape: Unlock and Onboarding scroll, keyboard usable.
- [ ] Screenshots are blocked on vault screens and in Recents (expected).
- [ ] Bottom bar and system gesture bar do not overlap.

## 7. The new look (0.4.0, Atomic design system)

Test this on the 0.4.0 build; the 0.3.0 phone test is on commit `cd7b0d0`.

- [ ] First start is paper (light). If you had picked dark before, it stays dark.
- [ ] Settings › Appearance: Light, Dark and Match system each apply at once
      and survive a restart; the status bar icons stay readable in each.
- [ ] Titles and buttons are tall capitals (Bebas Neue); body text is calm;
      passwords and usernames are in a monospace font and keep their case.
- [ ] A login opens in a read view: copy username, reveal and copy password,
      "Show 2FA code", fill receipts. EDIT opens the editor; after saving, the
      read view shows the new values; delete returns to the vault.
- [ ] Health shows the score with one reason; a finding opens the item.
- [ ] Phone font size at the largest setting: nothing clipped, titles wrap.
- [ ] Phone "Remove animations" on: buttons and sheets still work, with no movement.
- [ ] Autofill in Chrome and an app: the unlock screen says "Fill · <site>"
      and "Unlock and fill"; a look-alike site shows "This is not <site>".
- [ ] Launcher icon is the atom mark on dark.
- [ ] Anything that looks wrong or unclear: note the screen.
