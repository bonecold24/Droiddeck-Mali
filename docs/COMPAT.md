# Phase 6 — Game Compatibility Test Plan

Date: 2026-10-14. Target: Xiaomi 14T Pro — Dimensity 9300+ (MT6989),
Mali Immortalis-G720 MC12 (Valhall 5th-gen CSF, "v12"), HyperOS.

**Device identifiers (reconciled 2026-10-14):** the v2 Tier 0 phone report
shows `Xiaomi A402XM` (the internal/board identifier as reported by the OS
build properties); the retail Global model number is `2407FPN8EG`. Both refer
to the same device (Xiaomi 14T Pro, codename `rothko`). Reports should record
both strings.

**Gate A5/B4 revision (2026-10-14):** the D3D9/D3D11 Tier 0 stages are
documented below as `PREREQ_MISSING` — see "D3D Cube Execution Path" for the
full analysis. Do not present them as GPU tests until the prerequisites exist.

## Status up front

**No game has been executed in this phase.** There is no installable APK yet
(local Gradle assembly is blocked only by the sandbox proxy — no code verdict),
no ARM64 emulator on this x86_64 host, no real Mali hardware, and no Steam
test account. Per the mission's own rules, every game below is
**NEEDS-REAL-MALI**: this document is the execution plan the user runs on the
14T Pro via the built-in one-tap Compatibility Test mode (Phase 5, item 11),
not a results report.

Driver verdicts this plan is built on (see `docs/GPU.md`, all REPORTED, none
mission-observed): **D3D9 ready** (DXVK-Sarek path), **D3D11 target at
FL 11_1** (stock DXVK), **D3D12 stretch-only** (vkd3d-proton capped at
FL 11_1 / SM 6.0 — 6.8 after the denorm fix), **sparse NO-GO on kbase**
(D3D FL 12_0/12_1/12_2 unreachable, tiled tier forced 0).

## How a run works

1. Install the APK on the 14T Pro, complete setup, import the
   `panvk-kbase-g720` driver package (Phase 4 bundle is already in
   `app/src/main/assets/driver-bundles/`).
2. Open **Compatibility Test** → the suite runs unattended:
   Tier 0 system checks first, then each requested game in tier order.
3. Per test the runner records: outcome (`REACHED_MENU` / `REACHED_GAMEPLAY` /
   `CRASH` / `HANG` / `TIMEOUT` / `SKIPPED` / `ERROR`), avg FPS, 1% low FPS,
   max frame time, telemetry (CPU/GPU temp, battery, RAM) at start/end, and
   the exact environment (app version+commit, driver name/version/SHA-256,
   Vulkan API, Proton version, kbase UAPI read from the device).
4. Export **JSON + Markdown** and Share. Missing requested games are recorded
   as `SKIPPED` — they never fail the suite.
5. Send the report back; each outcome maps to the root-cause template in §6.

"Pass" definitions: **Tier 0** — binary renders its frame(s) without GL/VK
errors. **Tier 1/2** — `REACHED_MENU` = game's main menu renders and is
navigable; `REACHED_GAMEPLAY` = past menus into rendered 3D gameplay for at
least 60 s. FPS numbers are informational only on first runs; stability
(menu/gameplay reached, no crash/hang) is the gate.

The default requested list is wired in code:
`CompatTestSuite.defaultTierGames()` (branch `phase6/compat`, 7/7 host unit
tests pass — VERIFIED-CI). D3D10 has no separate cube: DXVK implements D3D10
on the D3D11 device, so the D3D11 cube passing covers the D3D10 path.

## Tier 0 — sanity (coded as system checks, always run first)

| Check | What it proves | Expected (REPORTED basis) |
|---|---|---|
| `vkcube` | PanVK kbase ICD loads, presents a frame | PASS — upstream PanProbe 38/38 on G720 MC12 |
| `vulkaninfo` capture | Full device/extension report; fills the kbase UAPI field (currently "unknown") and re-captures the capability matrix (gap G5) | PASS — also answers open device questions 1, 5, 6 in `docs/GPU.md` §8 |
| `d3d9-cube` | D3D9 path end-to-end | **PREREQ_MISSING** — see "D3D Cube Execution Path" below. No Wine/Proton/DXVK in the v3 guest; the stage cannot run and must not be reported as a GPU result. |
| `d3d11-cube` | D3D11 path end-to-end at FL 11_1 | **PREREQ_MISSING** — see "D3D Cube Execution Path" below. No Wine/Proton/DXVK in the v3 guest; the stage cannot run and must not be reported as a GPU result. |

If any Tier 0 check fails, stop: the failure is in the driver/ICD layer
(§6), not in any game.

## D3D Cube Execution Path — Gate A5/B4 findings (2026-10-14)

**Status: the D3D9/D3D11 Tier 0 stages cannot pass in v3/v3.1. They are
marked `PREREQ_MISSING`, not GPU tests.** This section documents exactly why,
what is missing, and the options to fix it. Evidence labels apply to each
claim.

### How the cubes are built (VERIFIED-CI)

- CI (`.github/workflows/build.yml`, step "Build D3D Tier-0 cubes
  (mingw-w64)") compiles `tools/comptest/d3d9-cube.c` and
  `tools/comptest/d3d11-cube.c` with **`x86_64-w64-mingw32-gcc`** into
  `app/src/main/assets/comptest/d3d9-cube.exe` and `d3d11-cube.exe`.
- These are **x86_64 Windows PE executables**. They link against the
  Windows D3D9 (`-ld3d9`) and D3D11/DXGI (`-ld3d11 -ldxgi`) import libraries.

### How the app tries to run them (code-read, needs phone confirmation)

- `CompatTestExecutor.ensureComptestPayload()` copies `assets/comptest/*`
  into the guest at `/opt/comptest/` and `chmod +x`s them.
- `guestArgv()` then executes **`/opt/comptest/d3d9-cube`** and
  **`/opt/comptest/d3d11-cube`** directly — note the **filename mismatch**
  (no `.exe` suffix) and, more fundamentally, **no Wine/Proton invocation
  at all**. A Windows PE binary cannot execute on Linux without a Windows
  compatibility layer; the exec will fail (exit 127 / exec-format error).

### What Wine/Proton is in the v3 guest (VERIFIED-CI via repo inspection)

**None.** The guest rootfs is a plain `debian:bullseye` Docker image
(`public.ecr.aws/docker/library/debian:bullseye`, pinned by SHA-256 in
`tools/linuxfs/debian-image.env`, published as release
`ci-debian-bullseye-r1`). It contains no Wine, no Proton, and no DXVK.
The `tools/linuxfs/overlay/` adds DroidDeck tooling only:

- `droiddeck-proton-run` — runs a Windows program **with the Proton the
  Steam client has chosen for an installed game**. Requires Steam to be
  installed, the game to be present, and a compat-tool mapping. Not usable
  for standalone Tier 0 cubes.
- `droiddeck-proton-extra` — downloads ARM64 Proton builds at runtime from
  `GloriousEggroll/proton-ge-custom` (aarch64), `CachyOS/proton-cachyos`
  (arm64), or `The412Banner/DroidDeck-Proton` (aarch64). A downloader, not a
  bundled Proton.
- `droiddeck-esync` — references Valve's `Proton Experimental (ARM64)` and
  `Proton 11.0 (ARM64)` as *sources*; expects them to already be installed
  (via Steam).
- `droiddeck-fex` / `droiddeck-appimage-run` — FEX x86_64 emulation tooling
  exists in the guest, but no x86_64 Wine is installed to run under it.

**Therefore B4 is answered: no Wine/Proton of any kind is installed in the
v3 guest.** Proton is a user-selected component (`ProtonMenu.kt`) downloaded
at runtime via Steam — it is not present at Tier 0 time.

### What is missing (all four must exist for the D3D stages to run)

1. **A Windows compatibility layer in the guest** — one of:
   - (a) Valve ARM64 Proton (runs ARM64/ARM64EC Windows PE),
   - (b) an ARM64 Wine build (e.g. from `The412Banner/DroidDeck-Proton`),
   - (c) FEX + x86_64 Wine (FEX tooling already in guest; x86_64 Wine not).
2. **DXVK (D3D11) / DXVK-Sarek (D3D9)** — the Vulkan-based D3D
   implementations. Neither is in the guest; Proton bundles its own DXVK,
   so (1a)/(1b) would bring DXVK along.
3. **Architecture match** — the cubes are x86_64 PE. Under (1a)/(1b) they
   would need rebuilding as **ARM64 PE** (`aarch64-w64-mingw32-gcc`); under
   (1c) the existing x86_64 PE cubes could run as-is under FEX-emulated
   x86_64 Wine.
4. **Correct invocation** — the executor must call Wine/Proton (or a
   wrapper script that discovers it), not exec the `.exe` directly; and the
   filename mismatch (`d3d9-cube` vs `d3d9-cube.exe`) must be fixed.

### Recommendation

For this release: **keep D3D9/D3D11 as `PREREQ_MISSING`** and add the
"prerequisites present?" stage (Gate A5) that fails with a clear message
listing the four missing pieces above. Do not claim the D3D stages can pass
until all four are answered.

For the next release, the smallest viable path is **(1b)+(3)** : bundle an
ARM64 Wine/Proton (e.g. `The412Banner/DroidDeck-Proton`, already wired as a
download source in `droiddeck-proton-extra`), rebuild the cubes as ARM64 PE,
and route the Tier 0 D3D stages through a wrapper that invokes
`droiddeck-proton-run`-style invocation with an explicit Proton path.
Alternative **(1c)**: install x86_64 Wine under the existing FEX tooling and
keep the current x86_64 PE cubes — fewer rebuilds, but an extra emulation
layer in the measurement path.

### kbase UAPI and Vulkan API fields — expected format

The Tier 0 report schema (`CompatReport.kt`) already carries `vulkanApi`
and `kbaseUapi` fields. Once the Stage 0 environment probe (Gate A4) and
the headless compute test (Gate B2) land, they should be filled as:

- `vulkanApi`: the Vulkan API version string reported by the loader/driver,
  e.g. `1.4.363` (the version the RC3 driver advertises upstream).
  Source: `vulkaninfo` `VkApplicationInfo.apiVersion` or the compute test's
  `apiVersion` printout.
- `kbaseUapi`: the Mali kbase kernel-interface version as read on-device,
  e.g. the `KBASE_API_VERSION` / ioctl interface revision obtained from
  `/dev/mali0` (or `"unavailable: <errno>"` if the device node cannot be
  opened from inside the guest). The v2 report left both fields
  `"unknown"` — they must be filled from the Stage 0 probe output, not
  guessed.

Until then, both fields remain `"unknown"` and any claim about them is
`NEEDS-REAL-MALI`.

## Tier 1 — light / free-to-play (D3D9–D3D11 era)

All three are free-to-play on Steam (long-standing F2P titles; re-verify
price = $0 on the Steam store page before installing, per mission rules —
owned or F2P only).

| Game (Steam app id) | Engine / API | Why it's Tier 1 | Expected | Known risks |
|---|---|---|---|---|
| Team Fortress 2 (440) | Source 1 / D3D9 | Oldest API, huge shader-cache corpus, historically the first title validated on new Proton/ARM stacks | `REACHED_GAMEPLAY` likely — best-odds first game | Shader-compile stutter on first run (mitigated by pre-compilation, pipeline libraries REPORTED present) |
| Unturned (304930) | Unity / D3D11 | Light Unity title, exercises the D3D11 path with real-world shaders | `REACHED_MENU` likely; gameplay TBD | Unity player quirks under Wine (usually config-level, not driver) |
| Brawlhalla (291550) | Custom / D3D11 (verify at runtime) | 2D-ish fighter, low GPU demand | `REACHED_MENU` possible | **Anti-cheat risk:** uses Easy Anti-Cheat — if the EAC EOS module isn't enabled for Proton, online play is blocked; offline/local modes may still run. Treat any EAC dialog as EXPECTED-BLOCKED, not a driver bug |

## Tier 2 — D3D11 free-to-play (the main catalog tier)

| Game (Steam app id) | Engine / API | Why it's Tier 2 | Expected | Known risks |
|---|---|---|---|---|
| Warframe (230410) | Evolution / D3D11 | Demanding D3D11, historically Gold on Proton | `REACHED_MENU` plausible; gameplay is the real test | Launcher (standalone updater) sometimes fights Wine; use the Steam launch path |
| Dota 2 (570) | Source 2 / D3D11 | Heavy D3D11 + **has a native Linux client** — may run natively in the userland without Proton at all | `REACHED_GAMEPLAY` plausible via either path | If native client is used, this tests the userland more than the driver |
| Path of Exile (238960) | Custom / D3D11 (Vulkan renderer also exists) | Shader-heavy ARPG; the in-game Vulkan renderer option is a second data point for the driver | Menu likely; gameplay TBD | Enormous shader corpus — pre-compilation matters |
| Paladins (444090) | Unreal Engine 3 / D3D9–D3D11 | UE3 is the classic DXVK workout | Menu likely | **Verify anti-cheat status at runtime** (EAC history unclear) — any anti-cheat dialog = EXPECTED-BLOCKED |

F2P status of all four is long-standing; re-verify $0 on the store page
before installing.

## Tier 3 — D3D12 (stretch tier, currently EMPTY)

Be explicit: **no Tier 3 candidates qualify.** The driver ceiling is
FL 11_1 (sparse NO-GO, `docs/GPU.md` §4); every AAA D3D12 title targets
FL 12_0 and will not start. vkd3d-proton device creation itself is still
unverified on v12 silicon (gap G1). Tier 3 stays empty until:
1. G1 is verified on the 14T Pro (vkd3d-proton `D3D12CreateDevice` succeeds),
2. a D3D12 title that runs at FL ≤ 11_1 is identified.

Do not waste phone time on D3D12 titles before (1). Tekken 8 (the user's
original ask) is D3D12-only / FL 12_0-class: it belongs in this tier and
does **not** qualify — say so plainly in the final report.

## What to record per game

Environment (automatic): app version + git commit, driver name/version/
binary SHA-256, Vulkan API version, Proton + DXVK-variant versions,
kbase UAPI as read on-device, device model + SoC.
Per test (automatic): outcome, avg FPS, 1% low, max frame time, telemetry
snapshots, crash logs where captured. Manual note for: menu vs gameplay
reached, visual corruption (describe it — corruption with a "passing"
feature bit is a driver bug, never ignore it), anti-cheat dialogs.

## Root-cause template (map failures to layers)

Use the G1–G13 gap list in `docs/GPU.md` §3.

- **Tier 0 fails** → driver/ICD layer. `vkcube` fail: `/dev/mali0` access or
  kbase UAPI mismatch (device profile must detect, never crash — Phase 5
  item 7). `vulkaninfo` extension missing vs REPORTED matrix: re-capture
  (G5), then driver-patch. D3D cubes fail: **first check the outcome is not
  `PREREQ_MISSING`** (Gate A5/B4 — no Wine/Proton/DXVK in the v3 guest means
  the D3D stages cannot run at all; this is an environment gap, not a driver
  bug). Only once the prerequisites exist does the DXVK-variant config
  (Phase 5 item 8) → BC decode quality (G6) → driver chain apply.
- **Tier 1 D3D9 fails** → DXVK-Sarek config → BC/AFRC shadow decode (G6,
  try `PANVK_BC_AFRC=0`) → driver. Menu renders but corrupt: feature bit
  reported but broken in practice — file as driver bug with the capture.
- **Tier 2 D3D11 fails at launch** → Proton/Wine version (try Proton-GE /
  ARM64EC per Phase 5 item 8) → DXVK version vs reported feature bits
  (G5/G7) → shader-compile / memory-heap issues (heap layout still unknown —
  `docs/GPU.md` §8 Q6). **Fails only in gameplay, menu fine** → perf/thermal
  or shader-pipeline issue, not a feature gap.
- **Any D3D12 attempt fails** → expected until G1 verified; after that,
  G2 (SM cap), G9/G10 (FL caps — G10 unfixable by design).
- **Steam client itself misbehaves** (black screen, no present) → G13
  presentation path (software MIT-SHM present, gamescope/Xwayland pacing) —
  app/compositor layer, verify before touching the driver.
- **Hang with no crash log** → process-lifecycle handling (Phase 5 item 9:
  redistributables, EULA, crash capture) before blaming the GPU.

## Anti-cheat / online-only: what will never work

Say it plainly — no driver update changes these; they are policy blocks,
not technical ones:

- **Kernel-level anti-cheat: never.** Vanguard (Riot: Valorant, League of
  Legends) requires a Windows kernel driver — impossible under Wine/Proton
  by design. Any Vanguard title is a permanent no.
- **Developer-blocked on Linux:** titles whose publishers explicitly refuse
  Linux clients — Destiny 2 (BattlEye, published ban policy), Fortnite (EAC,
  blocked by Epic). Do not test; the account risk is real.
- **EAC / BattlEye titles without Proton support enabled:** the game may
  install and even reach menus, then refuse online play. This is expected
  and is recorded as EXPECTED-BLOCKED, not a failure of the stack.
  (Brawlhalla and Paladins rows above carry this flag.)
- **Online-only games** additionally depend on network behavior in the
  userland that is itself unverified — single-player/offline modes are the
  honest test surface for this stack.

## Deliverables checklist

- [x] `docs/COMPAT.md` (this file) — execution plan, tiers, root-cause
      template, anti-cheat section
- [x] Default tier list wired: `CompatTestSuite.defaultTierGames()`
      (branch `phase6/compat`, commit `f6e68d03`), 7/7 host unit tests pass
      (VERIFIED-CI via kotlinc + JUnit 4 host harness)
- [ ] Every game row above: NEEDS-REAL-MALI — queued for the 14T Pro run
- [ ] Tier 3: empty by design until G1 verified on-device

## Evidence labels used in this phase

No game executed: everything is NEEDS-REAL-MALI. The only observed result
is the `defaultTierGames()` unit-test run: 7/7 pass, VERIFIED-CI
(kotlinc 2.1.0 + JUnit 4.13.2 host harness, pure-Kotlin sources, no Android
framework). APK assembly remains BLOCKED on environment (sandbox proxy
blocks the Gradle distribution download — no code verdict). No Valve
binaries touched; no Steam account created; GPL-3.0 attribution intact;
no GitHub writes.
