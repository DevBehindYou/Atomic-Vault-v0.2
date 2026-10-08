# Project Handover

Last updated: 2026-10-08
Verified against commit: `ab7c515` (work branch `claude/stoic-lamport-t601ql`)

## Compact Context

```text
@CTX/2
P:atomic-vault(android,offline password vault)
OBJ:phone-test+snapshot-review>release 0.4.0 (PR#20 green)
STACK:kotlin|compose|sqlcipher|argon2id|keystore|autofill|agp8.13|sdk36
DONE:PR#2-#26 merged to work branch (incl idle-lock,history,breach,csv,screen-lock,native-argon2,v0.4.0,backup-device-test)
PEND:phone_check.sh on device,manual checklist,snapshot review,release(user),package rename(after),history-in-backup?(user)
RULE:no-main-without-user,PR+green-CI,no-internet,no-secrets,report-%
STAT:build=V;unit=V;emulator=V;phone=N;snapshots=N;release=N
NEXT:run tools/phone_check.sh>checklist>snapshots>user merges #20+tags v0.4.0
```

## Goal

Ship the rebuilt vault (Autofill for Gboard, Atomic design system, sealed key
storage, SQLCipher swap, SDK 36, and five new features) after a real-phone test.

## Current Status

| Area | Status |
|---|---|
| Build / unit / lint | VERIFIED (CI) |
| Emulator checks (API 29 and 30) | VERIFIED (CI) |
| Upgrade from released 0.2.1 | VERIFIED (CI, release PR #20) |
| Backup restore on a device | VERIFIED (CI, PR #26) |
| Real phone | NOT_RUN |
| UI snapshot review | NOT_RUN |
| Release to `main` | NOT_IMPLEMENTED (user) |

## Active Problems

- None open. `ISS-001` (vault-creation OOM) fixed in PR #21 with native Argon2id.

## Highest-Priority Work

- `TASK-001`, then `TASK-002` and `TASK-003` (see `PENDING.md`)
- All agent-side work is done; ask the user about `TASK-008`

## Critical Constraints

- Never push or merge to `main`: it runs the signed release (D10).

## Read Next

- `CURRENT_STATE.md`, `PENDING.md`, `NEXT_AGENT.md`
