# Trixie Runtime Overlay — Engineering Documentation

## Purpose

The Trixie Runtime Overlay upgrades the Debian Bullseye (glibc 2.31) Linux guest on the phone to glibc 2.41, enabling the upstream PanVK glibc driver (which requires GLIBC_2.38+) to `dlopen()` successfully.

**Target device:** Xiaomi 14T Pro (rothko / 2407FPN8EG), Dimensity 9300+, Immortalis-G720 MC12.

## Overlay Contents

**Source:** `trixie-overlay-v1` GitHub release  
**Compressed SHA-256:** `e62b7c63a704ad63c8a914bf364f74f9efdcdff2cfbce24eef3caac1a24af73d`  
**Compressed size:** ~9.6 MB  
**Decompressed size:** ~42 MB (as `.tar` in APK)  
**File count:** 360 files (315 shared libraries)

### Complete File List

See `overlay-file-list.txt` for the full 360-file manifest. Key components:

**Loader:**
- `usr/lib/ld-linux-aarch64.so.1` → `aarch64-linux-gnu/ld-linux-aarch64.so.1` (relative symlink)

**glibc 2.41 family (Debian Trixie, consistent 2.41-12+deb13u4 set):**
- `usr/lib/aarch64-linux-gnu/libc.so.6`
- `usr/lib/aarch64-linux-gnu/libm.so.6`
- `usr/lib/aarch64-linux-gnu/libpthread.so.0`
- `usr/lib/aarch64-linux-gnu/libdl.so.2`
- `usr/lib/aarch64-linux-gnu/librt.so.1`
- `usr/lib/aarch64-linux-gnu/libresolv.so.2`
- `usr/lib/aarch64-linux-gnu/libnss_*.so.2`
- All glibc libraries verified as version 2.41 via `strings libc.so.6`

**Graphics/Wayland:**
- `usr/lib/aarch64-linux-gnu/libwayland-client.so.0.24.0` (real, version 1.24)
- `usr/lib/aarch64-linux-gnu/libwayland-stub.so` (DIAGNOSTIC STUB — see Known Issues)
- `usr/lib/aarch64-linux-gnu/libdrm.so.2`
- `usr/lib/aarch64-linux-gnu/libxcb.so.1`, `libxcb-dri3.so.0`, `libxcb-present.so.0`, etc.
- `usr/lib/aarch64-linux-gnu/libX11-xcb.so.1`

**SPIRV-Tools:**
- `usr/lib/aarch64-linux-gnu/libSPIRV-Tools.so` (real shared library)

**Other:**
- `usr/lib/aarch64-linux-gnu/libz.so.1.3.1`
- `usr/lib/aarch64-linux-gnu/libexpat.so.1`
- `usr/lib/aarch64-linux-gnu/libstdc++.so.6`
- `usr/lib/aarch64-linux-gnu/libbsd.so.0.12.2`
- `usr/lib/aarch64-linux-gnu/libffi.so.8.1.4`

## What Failed Originally

### Problem 1: glibc Version Mismatch
The phone's Debian Bullseye guest ships glibc 2.31. The upstream PanVK glibc driver requires GLIBC_2.38+. Static audit confirmed `dlopen()` would fail with version errors.

**Evidence:** Tier 0 report 2026-10-14 showed vkcube/vulkaninfo exit=1.

### Problem 2: Diagnostic Stubs
The initial overlay contained:
- `libSPIRV-Tools.so` as a minimal 5-symbol diagnostic stub
- `libwayland-stub.so` as a Wayland stub

Milo explicitly required these to be solved without stubs.

**Status:** `libSPIRV-Tools.so` is now the real shared library. `libwayland-stub.so` remains (see Known Issues).

### Problem 3: Overlay Format Mismatch
CI produced `trixie-runtime-overlay.tar.gz` but the APK contained `trixie-runtime-overlay.tar`. The Kotlin code expected `.tar.gz`.

**Fix:** Code now opens `.tar` and uses `tar -xf` (commit 2a0946d3d2).

## Verified QEMU Test Results

**Test environment:** qemu-aarch64-static, overlay libraries via `-L` flag  
**APK:** v3-preview-2 (`c0b274041069084eebfbac50ccef1a1fd727c6e7deaf54b01d2b3dc2d5951c48`)  
**Date:** 2026-10-15

### dlopen Test — PASS

```
$ qemu-aarch64-static -L overlay/usr ./dlopen-test ./libvulkan_panfrost.so
PASS: handle=0xaaaaaaac1300 vk_icdGetInstanceProcAddr=0x4000017fde54
```

**Evidence label:** VERIFIED-EMULATOR

All 18 driver dependencies satisfied by overlay libraries:
- libdrm.so.2, libm.so.6, libz.so.1, libwayland-client.so.0, libxcb.so.1, libX11-xcb.so.1, libxcb-dri3.so.0, libxcb-present.so.0, libxcb-xfixes.so.0, libxcb-sync.so.1, libxcb-randr.so.0, libxcb-shm.so.0, libxshmfence.so.1, libSPIRV-Tools.so, libexpat.so.1, libstdc++.so.6, libc.so.6, ld-linux-aarch64.so.1

### bash/apt/dpkg/vulkaninfo — NOT TESTED

These require a full Bullseye ARM64 rootfs, not just the overlay libraries. The overlay contains only libraries, not binaries.

**Evidence label:** NEEDS-ROOTFS

## Why qemu Was Available During A1 But Not Now

During the A1 verification (2026-10-14), the QEMU test ran in an environment where `qemu-aarch64` was already present. That test used a scratch sysroot with diagnostic stubs — a quick proof-of-concept, not a full rootfs test.

On 2026-10-15, this is a fresh VM environment. `qemu-user-static` was not pre-installed. It was installed via `apt-get install qemu-user-static` to run the proper verification against the published APK files.

The key difference: A1 was a scratch test with stubs; this is a clean-room verification of the actual published bytes.

## Rollback and Safety

### Current Status: NOT IMPLEMENTED

The overlay install routine (`GlibcDriverBundle.kt`) does NOT currently:
- Back up original loader/libs before replacement
- Apply changes atomically
- Recover safely on interruption
- Provide a "Repair guest runtime" button

### Required Implementation (Directive #5 Item 3)

1. **Backup:** Before replacing `/lib/ld-linux-aarch64.so.1` and libraries, copy originals to `/opt/droiddeck/backup/`
2. **Atomic apply:** Extract to temp directory, then rename into place
3. **Interruption recovery:** On startup, check for incomplete install marker; restore from backup if found
4. **Repair button:** UI button in CompatTestActivity that restores `/opt/droiddeck/backup/` to original locations

## NOTICE Entries

### Trixie Overlay Libraries
- **Source:** Debian Trixie (testing) ARM64 packages
- **glibc:** GNU C Library 2.41-12+deb13u4, LGPL-2.1
- **libwayland-client:** Wayland 1.24.0, MIT
- **libdrm:** libdrm 2.4.x, MIT
- **libxcb:** XCB 1.17.x, MIT
- **SPIRV-Tools:** SPIRV-Tools (Khronos), Apache-2.0

### PanVK Driver
- **Source:** zenithblue-oss/panvk-kbase-android, Mesa commit 5a07217f
- **License:** MIT
- **Upstream:** Mesa3D (https://www.mesa3d.org/), MIT

## Known Issues

1. **`libwayland-stub.so` present:** Diagnostic stub remains in overlay. Must be removed and replaced with real Wayland libraries before Gate A.
2. **No backup/rollback:** See Rollback section above.
3. **No full rootfs test:** bash/apt/dpkg/vulkaninfo not verified.

## v4 Plan: Full Consistent Trixie Rootfs

### Goal
Replace the Bullseye+overlay hybrid with a full Debian Trixie ARM64 rootfs, eliminating version skew and stub dependencies.

### Effort Estimate
- **Rootfs construction:** 2-3 days (debootstrap Trixie ARM64, verify all packages)
- **Size impact:** ~200-300 MB (vs 42 MB overlay) — requires download on first run
- **Testing:** 1-2 days (full QEMU verification of all Tier 0 stages)
- **Risk:** Medium — larger download, but eliminates glibc skew permanently

### Risk Assessment
- **Pro:** Single consistent userspace, no overlay complexity, all binaries match libraries
- **Con:** Larger download (~250 MB), longer first-run setup, more storage on phone
- **Mitigation:** Compress with zstd, download in background, show progress

### Recommendation
Proceed with v4 after Gate A passes on the overlay approach. The overlay proves the concept; v4 makes it production-ready.
