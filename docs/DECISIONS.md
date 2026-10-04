# Decisions

Short records of choices that shaped this work, so they are not re-litigated.
Newest last. "Owner" is who made the call.

## D1. Build and verify through GitHub Actions only (user)

The user's machine has no room for Android Studio or a Gradle cache, so nothing
is built locally. A first local attempt downloaded about 740 MB and was
abandoned. CI runs tests, lint, the debug build, UI snapshot renders and an
emulator check, and the results (PNGs, APKs, logs) are downloaded with the
GitHub CLI. `gh` is at `C:\Program Files\GitHub CLI\gh.exe` and is not on the
shell `PATH`. See [SESSION-LOG.md](SESSION-LOG.md) for the cache clean-up.

## D2. Follow the design reference's visual language, not its content (user + agent)

The user asked for the app to be built to `atomicvault_design_system_reference/`
and, separately, to implement only useful, reliable, needed functions,
components and screens, without clutter. So the flat canvas, surfaces, accent,
radii, type scale, key and button styles were taken, and mock-only content was
left out: defence index, "air-gapped core", hardware attestation, PGP chat,
passkeys, emergency access, keylogger simulator, decorative avatar/header.
The four-tab bottom bar was adopted because it maps onto real destinations.

## D3. Two Keystore keys for biometrics (agent)

`BiometricPrompt` with a `CryptoObject` needs a key that authenticates per use
(timeout 0); `Cipher.init` on a timed key throws outside its window. Autofill
and the keyboard cannot show a prompt and need a bounded no-UI window. One key
cannot serve both, so the DEK is wrapped under an **unlock** key (per-use) and
a **grace** key (30 s). Only the unlock key backs any credential *value* reveal;
the grace key only lets Autofill/keyboard match metadata. Consequence: existing
biometric setups are reset once on upgrade.

## D4. A package match never decides a web login (agent)

In a browser every site shares the browser's package. Web content is matched by
domain only; package matches count for native app screens only; an item that
stores a domain needs domain evidence. The keyboard sees no domain, so it never
reveals a domain-bearing item by package. Levels 2 and above are auto-offered;
level 0 (substring) never is. Look-alike and userinfo tricks are covered by
tests.

## D5. Autofill saves update, never replace (agent)

`updateItem` is a full replace. Autofill saves therefore build the update from
the existing item and change only username/password, and only update an item
that already holds the same username. Anything ambiguous creates a new item;
a duplicate is recoverable, an overwrite is not.

## D6. Clipboard clear does not read the clipboard (agent)

Backgrounded apps read nothing from the clipboard on Android 10+. The clear
tracks "has anything else replaced our clip" through change notifications and
clears unconditionally when nothing has. No hash or copy of the secret is
persisted. If the process is killed before the timer fires the clip remains;
that is a known limit.

## D7. Auto-lock means "after leaving the app" (agent, pending user review)

`0` is "lock immediately", so the option is labelled that way and "Never" was
removed (a password vault should not offer it). There is no on-screen idle
timer; adding one is an open product decision (see TASKS T3).

## D8. One dialog, flat surfaces (agent)

`LiquidGlassSurface` is flat (opaque fill, hairline border, pressed fill), with
no specular line, radial glow or ripple. There is a single `AtomicDialog`; the
sheet is also exposed as `AtomicDialogPanel` so it can be snapshot-tested,
because dialog windows are not part of a root capture.

## D9. Fonts stay on the system font until Inter is approved (open)

The reference uses Inter. Bundling it keeps the app offline (downloadable fonts
would contact Google), but it needs a ~1 MB download the user has not yet
approved. All text goes through the theme, so it is a one-place change.

## D10. Do not merge or release without the user (user)

The branch stays unmerged until the user has tested on a phone. A push to
`main` triggers the signed release workflow, so merging is a release decision.
A version bump (0.2.2 / `versionCode 3`) is proposed but not applied.

## D11. No custom keyboard; Autofill is the only fill path (user)

The Atomic keyboard (an `InputMethodService`) and the in-app unlock keyboard
were removed. Autofill already reaches the keyboard the user prefers: on
Android 11+ Gboard and other keyboards show Autofill suggestions in their
strip. A keyboard sees everything typed while it is selected and could only
fill one field, so it was both a bigger trust ask and a worse fill path. The
in-app unlock keyboard also locked out passwords with characters it lacked.
Master passwords are normalized to NFC so any keyboard produces the same key.


## D12. One per-use key; Autofill always authenticates in a screen (agent, supersedes D3)

The timed "grace" key from D3 let Autofill release the vault key with no UI
for 30 s after any fingerprint. In practice suggestions appeared only right
after a phone unlock and saves were silently dropped. Autofill now returns a
single "Unlock AtomicVault" chip while the vault is locked and authenticates in
`AutofillAuthActivity` (fingerprint bound to the per-use key, or master
password). The grace key is deleted on upgrade; the unlock key is unchanged.

## D13. Fingerprint on every fill (user-approved default)

When the vault is open in the app, Autofill shows account names but still asks
for the fingerprint (or master password) before filling. A "fill without asking
while unlocked" setting is possible later; the default stays strict.

## D14. Restores keep an undo copy in memory only (agent)

Before a restore replaces the vault, the current contents are kept decrypted in
process memory so "Undo restore" can put them back. They are dropped on lock
and never written to disk.
