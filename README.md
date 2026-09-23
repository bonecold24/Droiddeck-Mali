# SteamDeck

Run Valve's native ARM64 Steam client on an Adreno Android device. Steam runs in a Linux runtime under proot, with gamescope and an in-app Vulkan compositor. Windows games use Valve's ARM64 Proton and FEX. A desktop with LXQt, Firefox, and emulators is also available.

## Requirements and install

Use Android 9 or newer on a supported Adreno device (730 or newer, or 8xx). Mali, Xclipse, PowerVR, and Adreno 710 are unsupported. No root is required. Allow about 3 GB for the runtime and 1.1 GB more for the desktop and emulators. Install the APK from [Releases](https://github.com/The412Banner/SteamDeck/releases), install the Linux runtime, then press **Play** and sign in. Steam downloads on first launch. Install **Desktop & apps** to use the desktop and emulators.

On Android 12+, if Steam exits without a log, turn off **Restrict child processes** in Developer options.

## Build

Run `tools/build_local.sh` with Docker and the Android SDK/NDK installed. The APK is written to `app/build/outputs/apk/release/app-release.apk`. To install it on an attached device, run `tools/deploy_local.sh`.

## Limits

Compatibility and performance vary by device; hardware validation is limited. Desktop compositing uses software rendering. Firefox sandboxing is reduced under proot. See the session logs in `Download/SteamDeck/` when diagnosing problems.

## Contributors

Building this alongside the owner. A pull request from either of them builds by itself and gets
a comment saying whether it merges, what conflicts, or what failed to compile.

- **MaxsTechReview** - [@maxjivi05](https://github.com/maxjivi05)
- **Kurt Himebauch** - [@xXJSONDeruloXx](https://github.com/xXJSONDeruloXx)

<!-- contributions:start -->
_From the repository's pull requests; rewritten when one is opened, merged or closed._

| Contributor | Pull request | Opened | State |
|---|---|---|---|
| [@xXJSONDeruloXx](https://github.com/xXJSONDeruloXx) | [#1](https://github.com/The412Banner/SteamDeck/pull/1) initial decky installer stuff | 2026-09-23 | closed |
| [@xXJSONDeruloXx](https://github.com/xXJSONDeruloXx) | [#3](https://github.com/The412Banner/SteamDeck/pull/3) Fix hardware back in Steam session | 2026-09-23 | merged 2026-09-23 |
| [@xXJSONDeruloXx](https://github.com/xXJSONDeruloXx) | [#4](https://github.com/The412Banner/SteamDeck/pull/4) Update app launcher icon | 2026-09-23 | merged 2026-09-23 |
| [@xXJSONDeruloXx](https://github.com/xXJSONDeruloXx) | [#5](https://github.com/The412Banner/SteamDeck/pull/5) Add reproducible local build and deploy helpers | 2026-09-23 | merged 2026-09-23 |
| [@xXJSONDeruloXx](https://github.com/xXJSONDeruloXx) | [#6](https://github.com/The412Banner/SteamDeck/pull/6) Add Steam and QAM touch controls | 2026-09-23 | merged 2026-09-23 |
| [@xXJSONDeruloXx](https://github.com/xXJSONDeruloXx) | [#7](https://github.com/The412Banner/SteamDeck/pull/7) Add session suspend modes and audio recovery | 2026-09-23 | draft |

- **@xXJSONDeruloXx**: 6 submitted, 4 merged
<!-- contributions:end -->

## Credits and licence

GPL-3.0. Runtime, shim, input, and controller work build on WinNative and Bannerlator (maxjivi05); audio, gamepad, and session foundations build on Winlator (brunodev85). See [LICENSE](LICENSE). Steam and Proton belong to Valve Corporation; this project is not affiliated with Valve.
