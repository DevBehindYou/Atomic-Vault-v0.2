# Decisions

Canonical project decisions live in `docs/DECISIONS.md` (D1-D15; D10: never
merge or release without the user). Session decisions likely to matter:

## ADR-S1 — Screen-lock key only on Android 11+

Status: ACTIVE
Decision: phones without a fingerprint use a second Keystore key with
`AUTH_DEVICE_CREDENTIAL`, timeout 0 (auth on every use). Below API 30 only the
master password works.
Reason: per-use device-credential keys and CryptoObject prompts need API 30.

## ADR-S2 — Breach list bundled as a Bloom filter

Status: ACTIVE
Decision: SecLists `Pwdb_top-1000000.txt` (MIT), pinned by SHA-256, built by
`tools/breach_filter/build_breach_filter.py` into a 1.8 MB asset (0.1% false
positives). The wording says "appears in public leaks".
Rejected: online k-anonymity APIs (the app has no internet permission).

## ADR-S3 — Idle-lock and password-history storage

Status: ACTIVE
Decision: the idle-lock setting lives in plain preferences, like the theme
(not secret, needed while locked). Password history uses a sealed
`password_history` table and is not exported to backups.
Consequence (VERIFIED by `BackupRestoreDeviceTest`, PR #26): restoring a
backup clears the history. Open for the user as TASK-008.

## ADR-S4 — Release version 0.4.0 / versionCode 3

Status: ACTIVE
Decision: the release carries the 0.3.0 fixes and the 0.4.0 redesign, so it is
0.4.0 (PRs #23, #24; `docs/DECISIONS.md` D10 updated). 0.2.2 (from an old
TASKS line) was superseded.
