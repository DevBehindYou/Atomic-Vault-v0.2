# Next Agent

Do not restart from scratch.

## Load First

1. `STATE.yaml`
2. `HANDOVER.md`
3. `CURRENT_STATE.md`
4. `PENDING.md`

## Verify Repo Position

```bash
git fetch origin
git status --short
git branch --show-current
git rev-parse --short origin/claude/stoic-lamport-t601ql   # expect ab7c515 or later
```

## First Objective

`TASK-001`: automated phone check on the user's USB-connected phone.

## Execute

1. `git checkout claude/stoic-lamport-t601ql && git pull` (PR #19 with the script is merged).
2. `adb devices` shows exactly one phone. `gh auth status` is logged in.
3. Run `tools/phone_check.sh`. Keep the phone unlocked during the run (about 3 minutes).
4. Report the result. Then guide the user through `docs/PHONE-TEST-CHECKLIST.md`
   (TASK-002) and review the snapshots (TASK-003).
5. Record results in `TESTING.md` and update `STATE.yaml`.

## Working Rules (from the user and the project)

- Work in PRs against `claude/stoic-lamport-t601ql`. Merge them yourself only
  when CI is fully green and there is no code conflict. In docs-only conflicts
  (CHANGELOG, checklist) keep both entries.
- Never push or merge to `main` (D10).
- End commits with:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_01AeXJetPaNav7UG4TpETWKE`
  (a different agent should use its own attribution).
- Before pushing: `bash .github/scripts/design_check.sh` and
  `bash .github/scripts/unused_imports_check.sh` (ktlint).
- Tell the user progress in % (currently 28/31 = 90%; all remaining items need the phone or the user).

## Do Not Repeat

- `FA-001` to `FA-005` in `ISSUES.md` (FA-005: never move the KDF back onto the Java heap).

## Definition of Done (this phase)

- [ ] `tools/phone_check.sh` passes on the user's phone
- [ ] checklist sections 1-7 ticked, or bugs filed
- [ ] snapshots reviewed
- [ ] handover updated
- [ ] user asked about TASK-008 (password history in backups)
