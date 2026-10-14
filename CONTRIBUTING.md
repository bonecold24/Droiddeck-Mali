# Contributing to DroidDeck-Mali

## Ground rules

- **One change per branch, small commits.** CI must stay green after every merge.
- **Evidence labels on every test claim** (see below) — never claim a pass you did not observe.
- **Licenses:** this fork is GPL-3.0 (same as upstream DroidDeck). All contributions are
  under GPL-3.0. Keep upstream attribution intact; add new attributions to NOTICE.
- **No Valve binaries in the repo or APK.** Steam/Proton download from official sources at runtime.
- **User-space only.** No rooting, no kernel modules, no bootloader or system-partition changes.
- **Never spoof a Vulkan feature** without a test proving it does not corrupt rendering.

## Evidence labels

| Label | Meaning |
|---|---|
| VERIFIED-EMULATOR | Passed in the Android emulator lab (no Mali GPU — logic/launch chain only) |
| VERIFIED-CI | Passed in CI / host tests (unit tests, software Vulkan) |
| VERIFIED-REAL-MALI | Passed on a real Mali device (record the exact device) |
| NEEDS-REAL-MALI | Cannot be proven without Mali hardware |
| FAIL / BLOCKED | Failed, with logs attached |

The emulator has no Mali GPU, no mali_kbase device and no HyperOS behavior.
Never present software-rendered FPS as phone performance.

## Workflow

1. Fork / branch from `main`: `mali/<short-description>`.
2. Write the test first where applicable (what passes, how it is observed).
3. Implement, build (`./tools/build_local.sh`), run the relevant suite.
4. Record the result in `docs/TESTLOG.md` (item, test, expected, actual, logs, evidence label).
5. Open a PR; CI must be green; get review before merge.

## Reporting bugs

Use the issue templates. For Mali/GPU issues include: phone model, Android/ROM
(HyperOS version), GPU (`adb shell getprop` / vulkaninfo output), driver package
version, and the session folder from `Download/DroidDeck/` as a zip.
Do not send Mali-specific issues to upstream DroidDeck's tracker — they are
out of scope there.
