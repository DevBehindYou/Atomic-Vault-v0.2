# Project Context

## Project

AtomicVault: an offline Android password vault (logins, cards, identities)
with fill through Android Autofill (works with Gboard). No internet permission.
App id `com.atomicvault.android`; the debug build adds `.debug`, internal adds `.internal`.

## User Goal (this phase)

Finish the work on `claude/stoic-lamport-t601ql`, verify it on the user's own
(rooted) phone, review the UI, then release.

## User Preferences and Rules

- Work continuously; run CI in parallel through PRs; merge them yourself when
  testing passes and there are no code conflicts.
- Report progress in % regularly.
- Decided this session: "Lock after no taps" Off/1/5/15 min (default 5),
  separate from "Lock after leaving the app" (U4/T3). The user asked for and
  approved: screen-lock unlock, CSV import, offline breach check, password history.
- The user releases; never push to `main` (D10).

## Stack and Docs

See `STATE.yaml` stack. Source of truth for design: `docs/design/ATOMIC-DESIGN-SYSTEM.md`.
Decisions: `docs/DECISIONS.md` (D1-D15). Changes: `docs/CHANGELOG.md`.

## Out of Scope (for now)

- AGP 9 / Kotlin 2.4 migration; QR transfer; Inter font (needs approval, U3).
