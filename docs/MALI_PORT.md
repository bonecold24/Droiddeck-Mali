# Mali port — what was ported and what was adapted

This fork ports the MediaTek/Mali implementation from
[emir34-34/droiddeck](https://github.com/emir34-34/droiddeck) (`mediatek-mali-support`
@ `229fe07438a959a896703ce1205a6b41c2ad2987`) onto current upstream
[Droid-Deck/DroidDeck](https://github.com/Droid-Deck/DroidDeck) main.
Both are GPL-3.0; attribution is in [NOTICE](../NOTICE).

Upstream moved ~3 weeks between the fork point and the port, so the delta was
re-applied hunk by hunk rather than merged. The port lives on stacked branches
(merge in order):

| Branch | Contents |
|---|---|
| `mali/gpu-detection` | `DeviceSupport.mali()/mediatek()/canTry()`, `GpuInfo.Family.MALI` with experimental status, `detectMali()` (kbase gpuinfo → GL renderer → SoC fallback), Mali device-report lines |
| `mali/driver-ui` | No Turnip pair for Mali; compositor falls back to system Vulkan; Linux ICD import accepts any glibc `libvulkan_*.so` and keeps its name; Mali labels in driver menus; `DriverPairsTest` Mali cases; Mali hint in the GPU tab |
| `mali/session-runtime` | Session refused with a deep-linking dialog when Mali has no imported driver; Mali session-end log hints; `/dev/mali0` as the session's render node; Mali devfreq clock for the overlay |
| `mali/native-drm` | Compositor enables only the dma-buf extensions the driver has (clients fall back to wl_shm); `drm.c` GEM emulation covers `/dev/mali0` |
| `mali/panvk-build` | `tools/panvk/build-panvk-kbase.sh` + `patches/`, repointed at our driver fork; first imported Linux driver auto-selected on Mali |
| `mali/docs` | This file, `docs/development/mediatek.md`, the upstream bug-report reference |

## Adaptations (deliberate deviations from emir34-34)

1. **String resources, not hardcoded English.** Upstream moved UI text into `strings.xml`
   (l10n); the port adds Mali string resources (`*_mali*`) in English + Spanish instead of
   hardcoding.
2. **GPU tab, not the old driver panel.** Upstream replaced `GpuDriversPanel` with the
   Components page; the Mali hint now renders at the top of the GPU tab via `GpuDriversState.mali`.
3. **Driver source repointed.** emir34-34 builds PanVK from `wonderkast02/panvk-g720-kbase-csf`;
   we build from our own driver fork (`bonecold24/panvk-kbase-g720`, from
   `zenithblue-oss/panvk-kbase-android`), which is the tree validated on our exact SoC/GPU.
   Source is overridable via `PANVK_SRC_REPO`/`_BRANCH`/`_COMMIT`.
4. **Superseded PanVK patches dropped.** The cross-subqueue sync-scope and VT-lead-throttle
   patches were built on a wrong diagnosis (per emir34-34's own follow-up); only the final
   two patches are carried.
5. **`lvd_no_library` message generalized** to `libvulkan_*.so` since the import accepts any
   Mesa ICD now.

## Not ported

- Release notes/changelog entries specific to emir34-34's published APKs
  (`docs/releases/mali-fork.md`) — our releases are tracked in `CHANGELOG.md`.
- The `mediatek.md` "tested on POCO X8 Pro" claims are kept as background, not as our
  results. Nothing here is validated on the 14T Pro yet (Phase 4–6).
