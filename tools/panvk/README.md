# PanVK for kbase — glibc build tooling

`build-panvk-kbase.sh` cross-compiles PanVK (Mesa's Vulkan driver for Mali) for
aarch64 glibc from our driver fork (`bonecold24/panvk-kbase-g720`, itself forked
from `zenithblue-oss/panvk-kbase-android`), and packs the result as a zip the app
imports under **GPU drivers → Linux runtime driver**.

## Why a custom build

The driver fork ships bionic (Android) builds for WinTester-style apps. The Linux
runtime's processes are glibc and cannot load a bionic object, so the runtime
needs its own glibc `libvulkan_panfrost.so`. This script builds it without root
and without touching the host's packages (private LLVM prefix + Arch Linux ARM
sysroot).

## Usage

```bash
tools/panvk/build-panvk-kbase.sh [workdir]
# -> <workdir>/PanVK-kbase-glibc-DroidDeck.zip
```

Override the source with env vars (all optional):

- `PANVK_SRC_REPO` (default: our driver fork)
- `PANVK_SRC_BRANCH` (default: `main`)
- `PANVK_SRC_COMMIT` (default: empty = branch head; set for reproducibility —
  record the pin in `sources.lock`)

Needs on the host: git, curl, python3 (venv), pkg-config, bison, flex,
wayland-scanner, glslangValidator, spirv-tools, and the Android NDK
(`ANDROID_NDK_HOME`, for ld.lld/llvm-ar).

## Patches

Applied on top of the source tree before building (`patches/*.patch`):

- `0001-kbase-report-node-rdev-as-drm-render-device.patch` — report the kbase
  node's device number as the DRM render/primary device on glibc builds, so
  gamescope and dma-buf feedback match this device to the compositor's main
  device. (Upstream: emir34-34/droiddeck, GPL-3.0.)
- `0002-fix-provoking-vertex-fn-address-truncation.patch` — fix a 64→32-bit
  address truncation in the CSF provoking-vertex path that froze Steam's UI.
  (Upstream: emir34-34/droiddeck, GPL-3.0.)

Both were written against upstream Mesa PanVK; re-verify they still apply
whenever the source pin moves (Phase 4 owns this).

## Importing

In the app: **GPU drivers → Linux runtime driver → Import**, pick the zip.
On a Mali device the first imported driver is auto-selected.
