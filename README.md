# DroidDeck-Mali

**Steam on Android for MediaTek / Mali devices.** A fork of [DroidDeck](https://github.com/Droid-Deck/DroidDeck)
with MediaTek/Mali GPU support, targeting the Xiaomi 14T Pro (Dimensity 9300+, Mali Immortalis-G720 MC12).

> Status: early development. The upstream project supports Adreno (Snapdragon) GPUs only.
> This fork ports the MediaTek/Mali implementation (kbase/CSF path, PanVK driver import) so the
> Steam client can launch on Mali devices. Game compatibility is a work in progress — see
> [docs/COMPAT.md](docs/COMPAT.md) once testing begins.

## What this is

DroidDeck brings the SteamOS experience to Android: Valve's Steam client in Big Picture mode
on-device, with Windows games through Valve's ARM64 Proton. This fork adds:

- Mali GPU detection and a generic glibc Vulkan ICD import path (PanVK-on-kbase)
- `/dev/mali0` render-node handling and Mali devfreq performance overlay
- Mali-safe defaults (feature levels, DXVK variant selection)
- In-app driver selector with versioned PanVK driver packages and fallback chain

See [NOTICE](NOTICE) for upstream attribution. This project is not affiliated with Valve.

## Target device

| Field | Value |
|---|---|
| Device | Xiaomi 14T Pro (`rothko`), Global 2407FPN8EG |
| SoC | MediaTek Dimensity 9300+ (MT6989T), arm64-v8a |
| GPU | ARM Immortalis-G720 MC12 (Mali Valhall 5th-gen, CSF "v12") |
| OS | Android 14+ (HyperOS) |

No root required. No kernel modules, no bootloader changes — everything is user-space.

## Install (prebuilt)

1. Download the APK from [Releases](../../releases) (debug builds are published by CI on every push to `main`).
2. Install the APK, then install the Linux runtime when prompted (~3 GB).
3. Import a PanVK driver package: **GPU drivers → Linux runtime driver → Import** (get one from the
   [panvk-kbase-g720 releases](https://github.com/bonecold24/panvk-kbase-g720/releases), or build it yourself).
4. Turn off **Restrict child processes** in Developer options (required before Steam launches).
5. Press **Play** and sign in with your own legitimate Steam account.

> Only use your own Steam account and games you own (or free-to-play titles).
> No Valve binaries are bundled in this APK — Steam and Proton download from official sources at runtime.

## Build from source

Prerequisites: Docker, JDK 17, Android SDK + NDK. Or use the local script:

```bash
./tools/build_local.sh            # builds app/build/outputs/apk/debug/app-debug.apk
./tools/build_local.sh --release  # release APK (unsigned; sign it yourself, see below)
```

CI builds the debug APK on every push (see `.github/workflows/build.yml`).
Signed release APKs are produced on version tags (`v*`) — this requires the
`ANDROID_KEYSTORE_*` secrets to be configured in the repo settings.

## Upstream tracking

- Upstream: [Droid-Deck/DroidDeck](https://github.com/Droid-Deck/DroidDeck), pinned at the commit in [UPSTREAM_PIN](UPSTREAM_PIN).
- Mali delta ported from [emir34-34/droiddeck](https://github.com/emir34-34/droiddeck) (`mediatek-mali-support` branch) — see [docs/MALI_PORT.md](docs/MALI_PORT.md).
- To sync with upstream: `git fetch upstream && git merge upstream/main` (resolve conflicts, keep the Mali delta; CI must stay green).

## Docs

- `docs/DEVICE.md` — verified target device profile
- `docs/MALI_PORT.md` — what the Mali port changes and why
- `docs/HYPEROS.md` — HyperOS-specific notes (restrictions, developer settings)
- `docs/COMPAT.md` — game compatibility results (once testing begins)
- `docs/TESTLOG.md` — per-change test log with evidence labels

## License

GPL-3.0 — see [LICENSE](LICENSE). Shipped APKs are always accompanied by published source,
as the license requires.
