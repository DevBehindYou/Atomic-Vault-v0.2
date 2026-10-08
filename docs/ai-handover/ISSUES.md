# Issues

## Open

_None._

## Resolved (this session)

| ID | Problem | Fix |
|---|---|---|
| ISS-R1 | Minified build crashed Autofill on API 29: `NoClassDefFoundError InlineSuggestionsRequest` (newer R8 check-cast) | API-30 types confined to `autofill/InlineApi30.kt`; shared code passes `Parcelable` (PR #10) |
| ISS-R2 | Lint `ByteOrderMark` failure in `CsvImport.kt` | A Python edit had written a real U+FEFF; now compares `text[0].code == 0xFEFF` (PR #13) |
| ISS-R3 | Screen-lock emulator check: one BACK did not cancel the PIN panel on API 30 | Its keyboard takes the first BACK; the script presses BACK until the panel is gone (PR #15) |
| ISS-001 | App vanished after "Create vault" on emulators (runs #71, #75, #121; then every upgrade check on a minified base) | Root cause (VERIFIED via the PR #18 diagnostics): `OutOfMemoryError` — BouncyCastle Argon2id puts its ~66 MB matrix on the Java heap. Argon2id now runs natively via argon2kt 1.6.0 (off-heap), BouncyCastle kept as fallback; known-answer tests prove byte-identical keys (PR #21) |
| ISS-R4 | Duplicate OSGi MANIFEST from bcprov 1.81 | packaging exclude (PR #5) |

## Failed Approaches

### FA-001
Attempt: building or testing Android locally in the cloud container.
Why failed: `dl.google.com` (Google Maven) is blocked there. All verification goes through CI.
Do not repeat unless: on a local machine with Android SDK access.

### FA-002
Attempt: downloading CI artifacts or full job logs in the cloud container (`gh run download`, `gh api .../logs`).
Why failed: `*.blob.core.windows.net` was denied by the network policy. Job logs work through the GitHub MCP `get_job_logs` tool.
Do not repeat unless: that host is allowed, or the agent runs locally.

### FA-003
Attempt: treating a CI failure on Google Maven or Maven Central (HTTP 5xx or 429 while resolving plugins) as a code bug.
Why failed: they are transient. One re-run per failure is the rule; a second failure is real.

### FA-004
Attempt: writing Kotlin through Python string literals that contain `\uXXXX` escapes.
Why failed: Python turns the escape into the real character (see ISS-R2). Use Kotlin escapes inside a heredoc, or code comparisons.

### FA-005
Attempt: fixing ISS-001 with `android:largeHeap="true"` plus one `System.gc()` retry around the BouncyCastle Argon2 call.
Why failed: the minified build still threw `OutOfMemoryError thrown while trying to throw an exception`; largeHeap is not guaranteed and the matrix stays on the Java heap.
Do not repeat unless: never; keep the KDF off the Java heap (argon2kt). Any KDF change must keep the known-answer tests (`Argon2KdfVectorTest`, `Argon2KdfDeviceTest`) passing, or existing vaults stop unlocking.
