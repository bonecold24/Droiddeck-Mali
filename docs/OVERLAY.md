# Trixie Runtime Overlay — Safety Review

## Purpose
The overlay provides Debian Trixie (glibc 2.41) runtime libraries to the Bullseye (glibc 2.31) guest, enabling `dlopen()` of the PanVK driver which requires `GLIBC_2.38`.

## File List (Key Libraries)
From `trixie-runtime-overlay.tar.gz` (9.6MB, SHA256: `e62b7c63a704ad63c8a914bf364f74f9efdcdff2cfbce24eef3caac1a24af73d`):

| Library | Version | Source |
|---------|---------|--------|
| `libc.so.6` | 2.41 | Debian Trixie `libc6_2.41-12+deb13u4_arm64.deb` |
| `libm.so.6` | 2.41 | Same as above |
| `libpthread.so.0` | 2.41 | Same as above |
| `libdl.so.2` | 2.41 | Same as above |
| `ld-linux-aarch64.so.1` | 2.41 | Same as above |
| `libstdc++.so.6` | 14.2.0 | Debian Trixie `libstdc++6_14.2.0-19_arm64.deb` |
| `libgcc_s.so.1` | 14.2.0 | Debian Trixie `libgcc-s1_14.2.0-19_arm64.deb` |
| `libwayland-client.so.0.24.0` | 1.24.0 | Cross-compiled from `wayland-1.24.0.tar.xz` |
| `libSPIRV-Tools.so` | stub | Minimal stubs (5 functions) |
| `libdrm.so.2` | 2.4.124 | Debian Trixie `libdrm2_2.4.124-2_arm64.deb` |
| `libz.so.1` | 1.3.1 | Debian Trixie `zlib1g` |
| `libffi.so.8` | 3.4.8 | Debian Trixie `libffi8` |
| `libexpat.so.1` | 2.6.x | Debian Trixie `libexpat1` |

## Consistency Check
✓ All glibc components (libc, libm, libpthread, libdl, loader) come from the same `libc6_2.41-12+deb13u4` package — no mixing with Bullseye 2.31.
✓ libstdc++ and libgcc_s are from the same GCC 14.2.0 toolchain.
✓ The loader (`ld-linux-aarch64.so.1`) is 2.41, matching libc 2.41.

## What Originally Failed in A1
The PanVK driver (`libvulkan_panfrost.so`) requires:
1. `GLIBC_2.38` — Bullseye has 2.31, hence the Trixie overlay.
2. `wl_fixes_interface` and `wl_display_dispatch_queue_timeout` — introduced in Wayland 1.24.0. Trixie ships 1.23.1, so we cross-compiled 1.24.0 from source.
3. `libSPIRV-Tools.so` — not in Bullseye; we provide minimal stubs for the 5 functions the driver references (`spvContextCreate`, `spvBinaryToText`, `spvDiagnosticDestroy`, `spvDiagnosticPrint`, `spvTextDestroy`).

QEMU verification: `dlopen(RTLD_NOW)` + `dlsym(vk_icdGetInstanceProcAddr)` returns `PASS`.

## Installation Safety
- **Atomic loader update**: The `/lib/ld-linux-aarch64.so.1` symlink is updated via `ATOMIC_MOVE` after the new libc is in place. There is no window where a mismatched loader/libc pair exists.
- **Backup**: Original Bullseye libs are NOT backed up (they remain in the package manager database; `apt` can reinstall). The overlay marker file (`.trixie-overlay-installed`) prevents re-installation.
- **Interruption**: If installation is interrupted, the marker file won't exist, and the next run will retry. Partial files are overwritten.
- **Rollback**: The "Repair guest runtime" button (to be added) will remove the marker and re-extract, or restore from a backup if implemented.
- **UsrMerge handling**: Detects if `/lib` is a symlink (UsrMerge) or directory (Bullseye). For Bullseye, installs to `/lib/aarch64-linux-gnu` (searched before `/usr/lib` by ld.so).

## NOTICE Entries
- **glibc 2.41**: GNU LGPL v2.1. Source: https://www.gnu.org/software/libc/ / Debian: http://deb.debian.org/debian/pool/main/g/glibc/
- **Wayland 1.24.0**: MIT License. Source: https://gitlab.freedesktop.org/wayland/wayland/-/releases/1.24.0
- **Debian packages**: Various licenses (see `/usr/share/doc/*/copyright` in the .debs). Sources at http://deb.debian.org/debian/

## v4 Plan: Full Trixie Rootfs
**Effort**: Medium (2-3 days). Replace the Bullseye guest with a full Trixie rootfs.
**Risk**: Low-Medium. Trixie is stable; the main risk is testing all guest functionality.
**Benefits**: Eliminates the overlay complexity, ensures full library consistency, simplifies maintenance.
