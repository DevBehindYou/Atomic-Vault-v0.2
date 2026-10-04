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

## 3. Autofill (two bugs were fixed here, please test both)

- [ ] Settings, Arm autofill, choose AtomicVault as the autofill service in Android settings.
- [ ] In Chrome, log in to **site A** and accept "Save to AtomicVault". Do the same on **site B**. Both appear as separate items.
- [ ] Open site B's login page: only B's login is offered. Site A's is not.
- [ ] Log in to site A with a second account: a new item is created, the first account is untouched.
- [ ] Add notes, a TOTP secret and a tag to a saved login, then change its password through Chrome's save prompt. The notes, TOTP secret and tag are still there afterwards.
- [ ] A native app login: saved and offered in that app only.
- [ ] Tapping a suggestion asks for the fingerprint before anything is filled.

## 4. Vault basics

- [ ] Home `+` offers Login, Payment card, Identity; each opens its own editor and saves.
- [ ] Edit a card that has a tag: the tag survives. Delete a card and an identity (new Delete button).
- [ ] Generator tab: length slider, toggles, Copy. Paste elsewhere; the clipboard clears after about 45 s.
- [ ] Audit tab: score, counts, a finding opens its item.
- [ ] Backup and restore: export, then import into a clean install; items, tags and folders come back.

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
