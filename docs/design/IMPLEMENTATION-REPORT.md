# Atomic design system in AtomicVault: implementation report (0.4.0)

Spec: [ATOMIC-DESIGN-SYSTEM.md](ATOMIC-DESIGN-SYSTEM.md). Plan: [IMPLEMENTATION-PLAN.md](../IMPLEMENTATION-PLAN.md),
section 8. Design canvas (18 boards): https://claude.ai/artifact/KXY28fKQRhZcqpL4cgaGya.
Branch `claude/atomic-vault-analysis-1fj2ok`; nothing is merged or released.

## Evidence

| CI run | Commit | What it covered | Result |
|---|---|---|---|
| #36 | `b8c2754` | Tokens, fonts, components, contrast test, catalogue snapshots | Green; emulator flow and Autofill tap passed |
| #37 | `5eedb09` | Unlock, create vault, home, item detail, Autofill screens | Green, emulator included |
| #39 | `f091a3b` | Generate, Health, Settings, Backup | Green, emulator included |
| #40 | `7af9198` | Privacy proof, timeline, editors | Green, emulator included |
| #41 | `52e4533` | Old design layer removed, design check in CI | Failed: one test still used the deleted background (fixed in the next commit; the design check now scans tests too) |
| #42 | `5850ed6` | Everything above, obsolete test removed, design check on tests | Green: unit tests, lint, design check, snapshots, emulator flow incl. Autofill |

#35 and #38 failed to compile (an experimental-API opt-in, a missing import); each was fixed in the next push.

The app sets `FLAG_SECURE`, so emulator screenshots are blank by design. The
visual evidence is the Roborazzi snapshots that CI renders (catalogue in
light, dark and 200% font, item detail, every screen). This environment
cannot download CI artifacts, so **no one has looked at the rendered pixels
yet**. That is part of the phone test.

## What was taken from the spec

Ink on paper with one accent (Signal `#3A2FF0`); Bebas Neue / Hanken Grotesk /
JetBrains Mono with their roles; the 4 dp grid; radii 3/4/6/8/28/pill; ink
borders by role; hard offset shadows with no blur; the motion timings with
`ease` and no springs; status pills, section headers with ink rules, settings
rows, fact sheets, danger zones, ink modules; the copy rules (caps verb +
object buttons, mono caps labels, ISO dates, `·` `/` `→`); the contrast table.

Vault-specific adaptations (plan 8.3): Energy and Coins are not invented;
their patterns carry real data instead (the health card, `CLOUD: NONE`). Secrets
show in mono in their real case. User-named things keep their case and script.
Fonts are bundled, never downloaded. The strength bar keeps the family's colour
meanings. The dark theme is the spec's §13.9 variant.

## Architecture

- `ui/theme/`: `AtomicTokens` (raw colours); immutable `AtomicPalette` (light,
  dark) provided through `AtomicTheme.colors`; `AtomicFonts` / `AtomicType`;
  `AtomicRadius`, `AtomicSpacing`, `AtomicBorder`, `AtomicElevation` +
  `Modifier.hardShadow`; `AtomicMotion` + `LocalReducedMotion`;
  `ThemePreferenceStore` with Light / Dark / Match system. Mapped onto
  Material 3 so stock components follow the palette.
- `ui/components/`: `AtomicComponents.kt` (buttons, text field, chip, strength
  meter), `AtomicSystem.kt` (cards, panels, modules, rules, headers, tags,
  pills, bars, icon buttons, stepper, segmented toggle, settings rows, fact
  sheets, stat tiles, code wells, empty/loading/warning/danger states),
  `AtomicChrome.kt` (top bar, bottom bar, icon tile), `AtomMark.kt` (atom mark,
  dot grid), `AtomicDialog.kt`, `AtomicSwitch.kt`.

## Screens rebuilt

Unlock, Create vault, Vault home (with first-use and no-match states and the
add stack), **Item detail (new)**, Login editor, Generate (and the generator
inside the editor), Health, Settings (split in two files), Backup and restore,
Privacy proof, Security timeline, the three Autofill screens (unlock to fill,
save, look-alike warning), and the Autofill dropdown row. The card and
identity editors got the shared components, copy and outlined icons, but not
a structural rebuild (see below).

## UX changes that matter

- Logins open in a read view; editing is an explicit action.
- Health names the one thing to fix first; findings open the item.
- Counts are real: `42 ITEMS · ON THIS PHONE`, `12 SHOWN`, `6 / 6` checks.
- Destructive actions say what happens and what is kept ("Backups you already
  made still contain it", "You can undo until you lock").
- Errors say how to fix them ("These don't match yet. Check the last few
  characters.").
- Appearance: Light / Dark / Match system; anyone who picked dark before keeps it.
- Fixed while restyling: the masked password field in the editor hid its copy
  button.

## Accessibility

A unit test checks every text/surface pair in both palettes against WCAG AA.
Touch targets are 48 dp, including chips and icon buttons. Icon-only tabs and
buttons have names (tested). Status pills read "Autofill: on". Section titles
are headings. Mono labels are 12 sp or larger. Reduced motion follows the
system animation setting. A catalogue snapshot renders at 200% font.

## Performance

The theme is one immutable palette behind a static composition local, instead
of about 50 global observable states. Hard shadows are one outline draw with
no blur or layer. The animated background grid is gone. Fonts are local
(about 380 KB, three files). Lists use stable keys and content types.

## Removed

`LiquidGlassSurface`, `AmbientVaultBackground`, 30 legacy colour aliases,
`AtomicFontSize`, `AtomicFontWeight`, the bouncy `GlassSpring` and glass easing,
the stock `simple_list_item_1` dropdown, the old launcher icon, the filled
icons, and the editor's duplicate 2FA and fill-receipt rows. A CI design check
stops them coming back.

## Remaining (genuinely not done)

1. **Tablet and landscape layouts** (plan 8.9): forms and lists cap their width,
   but there is no navigation rail or list + detail split yet.
2. **Bottom sheets** (plan 8.5): the dialog is restyled but is still a
   centred dialog, not `AtomicSheet`.
3. **Card and identity editors**: not yet on a shared `ItemEditorScaffold`, and
   cards and identities still open straight into their editors (no read view).
4. **Raw dp values** remain in screen code (mostly 4–12 dp gaps); the design
   check covers colours, not sizes.
5. **Verify block** on Privacy proof (APK signing certificate SHA-256): not added.
6. **Visual review**: the rendered snapshots have not been looked at by a person,
   and the phone test (including dark mode and 200% font) is still to do.
7. Version numbers are unchanged (0.3.0 and 0.4.0 are decided but not applied).
