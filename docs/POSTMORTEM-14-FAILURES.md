# Post-Mortem: 14 Failed v3.1 CI Runs

## Summary
14 consecutive failures of the "Build APK" workflow between 2026-10-14 18:22 UTC and 2026-10-15 01:22 UTC, blocking v3.1.

## Root Cause
A bad merge (`git merge -X theirs`) of Gate A4 and B2 branches corrupted three Kotlin files with duplicate function definitions and mixed old/new APIs. The `-X theirs` strategy silently kept both versions of conflicting code instead of requiring manual resolution.

## Failed Runs

| Run ID | Failing Step | Root Cause | Fix |
|--------|--------------|------------|-----|
| 38614622379 | Build (Kotlin compile) | Duplicate `guestArgv()`, `ensureComptestPayload()`; mixed `TestOutcome.PREREQ_MISSING` vs `PrereqMissing()` | Removed duplicates, fixed API (ef95a55e) |
| 38616289050 | Build (Kotlin compile) | Same as above (ran before fix pushed) | Same fix |
| 38617372654 | Build (Kotlin compile) | Same (stale) | Same fix |
| 38617959324 | Build (Kotlin compile) | Same (stale) | Same fix |
| 38619078427 | Build (Kotlin compile) | Same (stale) | Same fix |
| 38619068544 | Build (Kotlin compile) | Same (stale) | Same fix |
| 38624087013 | Unit tests | A4 test files expected B2 features that were removed | Restored A4 test files (d0474a32) |
| 38626412408 | Unit tests | Same | Same fix |
| 38627505023 | Skipped | Workflow issue | N/A |
| 38627550884 | Skipped | Workflow issue | N/A |
| (4 more) | Various | Merge corruption in CompatTestSuite.kt, CompatTestTypes.kt | Restored from 84f66b0e |

## Prevention
1. **Never use `-X theirs`/`-X ours` for code merges** — always resolve conflicts manually.
2. **One writer to main** — all other work in branches, merge only when CI is green on the branch.
3. **Fast branch workflow** — added `.github/workflows/branch-check.yml` that runs only compile + unit tests (no APK signing, no asset bundling) for quick feedback.
4. **Small commits** — re-apply work in the order specified in the directive, verifying CI after each.

## Recovery
Created `recovery/v3-clean` branch from v2 (443020f). Work will be re-applied in order:
(a) trixie overlay + install hook + loader update
(b) A2 driver-selection migration
(c) A3 upstream Android driver provenance
(d) A4 diagnostics and Stage 0 probe
(e) B3 outcome taxonomy
(f) B2 compute test and display probe
