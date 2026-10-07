# Project Handover

Last updated: 2026-10-07
Verified against commit: `bb60f28` (work branch `claude/stoic-lamport-t601ql`)

## Compact Context

```text
@CTX/2
P:atomic-vault(android,offline password vault)
OBJ:phone-test+snapshot-review>release 0.3.0/0.4.0
STACK:kotlin|compose|sqlcipher|argon2id|keystore|autofill|agp8.13|sdk36
DONE:PR#2-#18 merged to work branch (incl idle-lock,history,breach,csv,screen-lock)
PEND:phone_check.sh on device,manual checklist,snapshot review,release,package rename
RULE:no-main-without-user,PR+green-CI,no-internet,no-secrets,report-%
STAT:build=V;unit=V;emulator=V;phone=N;snapshots=N;release=N
NEXT:merge#19>run tools/phone_check.sh>checklist>snapshots>user releases
```

## Goal

Ship the rebuilt vault (Autofill for Gboard, Atomic design system, sealed key
storage, SQLCipher swap, SDK 36, and five new features) after a real-phone test.

## Current Status

| Area | Status |
|---|---|
| Build / unit / lint | VERIFIED (CI) |
| Emulator checks (API 29 and 30) | VERIFIED (CI) |
| Real phone | NOT_RUN |
| UI snapshot review | NOT_RUN |
| Release to `main` | NOT_IMPLEMENTED (user) |

## Active Problems

- `ISS-001`: intermittent "app gone after Create vault" on emulators (3 times)

## Highest-Priority Work

- `TASK-001`, then `TASK-002` and `TASK-003` (see `PENDING.md`)

## Critical Constraints

- Never push or merge to `main`: it runs the signed release (D10).

## Read Next

- `CURRENT_STATE.md`, `PENDING.md`, `NEXT_AGENT.md`
