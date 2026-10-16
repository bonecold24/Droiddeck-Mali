# Mali G720 Pipeline — Reference Implementation Study

**Date:** 2026-10-17
**Purpose:** Document the working launch pipeline from reference implementations
before writing code (Directive: Master Autonomous, Phase 1).

## Key Finding: X11, Not DRM Gamescope

The fundamental mistake in v3-preview-5 through v3-preview-10 was trying to force
`gamescope --backend headless` with fake `/dev/dri/card0` nodes on `/dev/mali0`.

**Working implementations do NOT use DRM/KMS or headless Gamescope on Mali.**
They present frames via **X11 WSI** (`VK_KHR_xcb_surface` + `MIT-SHM 1.2 AttachFd`
shared memory) to a built-in X11 server.

### zenithblue-oss/panplay (v1.2.6/v1.2.7)

**Source:** https://github.com/zenithblue-oss/panplay

PanPlay README states:
> "Windows game launcher for Android: Wine (Proton arm64ec) + FEX + DXVK +
> a built-in X server, with the PanVK Kbase driver bundled so Mali CSF GPUs
> (tested: Mali-G615) can run Direct3D 9/10/11 games through Vulkan."

Key components (from `scripts/components.env`):
- **DXVK:** v3.1.1-4-arm64ec (commit b1a1c99ab52b687cf950d62c88bc2fa316b41663)
- **FEX:** FEX-2609.1 (commit 9fbdc00bd6401aff3b32d79e78ff98b8a13e4dcf)
- **Box64:** 0.4.5-2f47bdf (for WoW64, MIT licensed)
- **VKD3D-Proton:** 3.0.1-452-g22307558 (D3D12, incomplete on Mali)
- **Wine:** Proton arm64ec build
- **X Server:** Built-in X server derived from Winlator (LGPL-2.1) under
  `app/src/main/java/com/winlator/` and `app/src/main/cpp/winlator/`

**Pipeline:**
1. App starts built-in X server (Winlator-derived, on-screen)
2. Sets `DISPLAY=:0` (or similar)
3. Configures PanVK ICD via `VK_DRIVER_FILES` / `VK_ICD_FILENAMES`
4. Launches Wine (Proton arm64ec) with FEX for x86_64 emulation
5. DXVK provides D3D9/D3D10/D3D11 via Vulkan
6. Wine creates X11 windows via `VK_KHR_xcb_surface`
7. Frames presented through MIT-SHM shared memory to the X server

**No Gamescope. No DRM. No /dev/dri/card0 faking.**

### zenithblue-oss/panvk-kbase-android

**Source:** https://github.com/zenithblue-oss/panvk-kbase-android

Provides the PanVK Kbase driver (0.1.0-rc3) for Mali CSF GPUs.
- Tested: 38/38 PanProbe tests pass on Mali-G615
- Also validated on Immortalis-G720 (Dimensity 9300+ tablet)
- Driver: `libvulkan_panfrost.so` (glibc, aarch64)

### DroidDeck Main Play Pipeline

**Sources:** emir34-34/droiddeck (commit 229fe074), Droid-Deck/DroidDeck (07926175)

The main DroidDeck app's **Play** button (not CompatTestActivity) launches:
1. Linux guest container (proot)
2. Steam via Proton
3. Games via Steam

The pipeline is gated behind Adreno-only device checks in some versions.
On Mali, these checks must be bypassed.

**DroidDeck-Proton 11-9.1** (https://github.com/Droid-Deck/DroidDeck-Proton):
- ARM64 Wine + FEX-Emu + DXVK + VKD3D-Proton
- 596 MB tarball
- Wine binary: `files/bin-arm64/wine` (ELF 64-bit LSB, ARM aarch64)
- FEX integration: `libwow64fex.dll`, `libarm64ecfex.dll`
- DXVK DLLs: `files/lib/wine/dxvk/{x86_64,aarch64,i386}-windows/`
- Default prefix: `files/share/default_pfx_arm64/`

## Mali G720 Pipeline (Target)

### Environment Variables
```
DISPLAY=:0                          # X11 server (Xlorie or built-in)
VK_DRIVER_FILES=/opt/comptest/icd.json
VK_ICD_FILENAMES=/opt/comptest/icd.json
WINEPREFIX=/opt/proton/prefix
WINEDLLOVERRIDES=d3d9,d3d10core,d3d11,dxgi=n,b
DXVK_HUD=devinfo,fps,api
WINEDEBUG=-all
```

### Launch Sequence
1. Start X11 server (Xlorie on Android, or Xvfb :99 for headless tests)
2. Export DISPLAY
3. Ensure PanVK ICD at /opt/comptest/icd.json points to glibc libvulkan_panfrost.so
4. Initialize Wine prefix from default_pfx_arm64 (with DXVK DLLs)
5. Launch: `wine /path/to/game.exe` (FEX handles x86_64 → ARM64)
6. DXVK translates D3D9/11 → Vulkan → PanVK → Mali G720
7. Present via X11 WSI (VK_KHR_xcb_surface)

### What NOT to Do
- ❌ Do NOT use `gamescope --backend headless` on Mali
- ❌ Do NOT fake `/dev/dri/card0` or DRM nodes
- ❌ Do NOT rely on `VK_EXT_physical_device_drm` for presentation
- ❌ Do NOT use LD_PRELOAD drm-shim for Gamescope (use X11 instead)

### B0 vk-render-test Pattern (from PanProbe)

PanProbe passes 38/38 on G720 with the 0.1.0-rc3 driver. The render test must match
PanProbe's exact pattern:
- Render pass with color attachment
- Vertex buffer with triangle data
- Push constants (not UBO) for simplicity
- Image layout transitions: UNDEFINED → COLOR_ATTACHMENT_OPTIMAL → TRANSFER_SRC_OPTIMAL
- Memory barriers between render and transfer
- vkCmdCopyImageToBuffer for readback
- vkInvalidateMappedMemoryRanges before CPU read
