# MediaTek (Arm Mali) support

Status: **experimental.** The Mali port is adapted from
[emir34-34/droiddeck](https://github.com/emir34-34/droiddeck) (`mediatek-mali-support`),
where the Steam client was observed working on a POCO X8 Pro (Mali-G720 MC8) while games
did not run yet. This fork targets the **Xiaomi 14T Pro (Dimensity 9300+, Mali Immortalis-G720
MC12)** and has not yet been validated on that hardware — see [Known gaps](#known-gaps).
Adreno remains the main supported GPU family.

A DroidDeck session uses two Vulkan drivers (see `gpu/TurnipDriver.java` and
`gpu/LinuxVulkanDriver.java`):

| Half | Process | Adreno | Mali (MediaTek) |
|---|---|---|---|
| Display: the app's compositor puts frames on the panel | bionic, in the app | bundled Turnip through adrenotools | the system's Mali driver (`libvulkan.so`) |
| Runtime: Steam's UI (Zink), DXVK/VKD3D, gamescope, the desktop | glibc, under proot | the runtime's Turnip, or an imported `-Linux` Turnip | an **imported** glibc Mali driver; the runtime has none |

## What the app does on a Mali

- **Detection** (`core/DeviceSupport.kt`, `gpu/GpuInfo.kt`): a Mali is recognised by the kbase node
  `/dev/mali0`, `/sys/class/misc/mali0`, or the vendor's Mali userspace. It is reported as
  family `MALI`, support `UNTESTED`, and named from kbase's `gpuinfo` (`Mali-G720`) where the kernel
  lets the app read it, else the GL driver's renderer string. The install and session-start warnings
  say what is still needed instead of "not an Adreno".
- **Display driver**: Auto picks no bundled Turnip and the compositor loads the system Vulkan
  driver. The compositor no longer requires the dma-buf import extensions it cannot have
  (`vk_present.c`): it enables only what the driver offers, so a driver without
  `VK_EXT_image_drm_format_modifier` still gets a device. Without them, clients fall back to
  `wl_shm` (copies, slower) instead of the session failing outright.
- **Runtime driver**: the Linux runtime driver import accepts any glibc `libvulkan_*.so`, not only
  `libvulkan_freedreno.so`, and keeps its name (`meta.json`'s `libraryName`). The session hands it to
  the Vulkan loader exactly as it does an imported Turnip. With no imported driver on a Mali the
  session start is refused with a dialog that deep-links to GPU drivers, and the session log says so.
- **GPU node**: proot presents `/dev/mali0` as the session's DRM render node (named `panfrost`, as
  mainline names a Mali's), the way it presents KGSL on an Adreno, so gamescope offers linux-dmabuf.
  The runtime's `drm.c` preload emulates GEM handles for that node too (takes effect once the
  runtime is rebuilt with it).
- **Overlay stats**: the GPU clock is read from the Mali devfreq node (`/sys/class/devfreq/*mali*`)
  where KGSL is absent.
- **Driver UI**: on Mali the GPU tab explains the setup (imported PanVK as runtime driver, display
  driver on Auto) instead of offering Adreno-only driver pairs; the first imported Linux driver is
  auto-selected.

## What a Mali user needs

A glibc, AArch64 Mesa build of PanVK that talks to Android's **kbase** kernel interface (mainline
PanVK expects the panfrost/panthor DRM driver, which Android kernels do not have).
`tools/panvk/build-panvk-kbase.sh` builds one from our driver fork
(`bonecold24/panvk-kbase-g720`, forked from `zenithblue-oss/panvk-kbase-android`), plus
`tools/panvk/patches` (the kbase node's device number reported as the DRM render and primary
device, which is how the runtime presents `/dev/mali0` and what gamescope's nested backend needs;
and the provoking-vertex address-truncation fix).

Import the zip under GPU drivers > Linux runtime driver; on a Mali the first one imported is
selected. It targets Valhall CSF GPUs: Mali-G610/G615/G710/G715/G720.

## Known gaps

- **Games do not run yet** (observed on the POCO X8 Pro by emir34-34, 2026-10-04; untested on the
  14T Pro):
  - **DXVK refuses the GPU.** `Skipping: Device does not support required feature 'multiViewport'`;
    next is `textureCompressionBC`. The G720 has no BC/DXT in hardware. PanVK also lacks
    `shaderClipDistance`/`shaderCullDistance`, which DXVK requires. DXVK has no fallback for any of
    these. The way forward is BC emulation in PanVK (decode to RGBA8 on upload) — Phase 4 work,
    starting from the BC→ASTC work in the driver fork's network.
  - **WineD3D** (`PROTON_USE_WINED3D=1`) got one game to its splash screen, then crashed loading
    the menu. Still untested on this fork's target.
- Whether the system Mali driver exposes `VK_EXT_image_drm_format_modifier` depends on the DDK
  version; the compositor's log (`present: driver MISSING ...`) says which it lacks.
- GPU load for the overlay is not read on Mali yet; the clock-pin option is KGSL-only.
- Xclipse (Exynos 2200+) and PowerVR remain unsupported.
- The `tools/panvk/patches` were written against upstream Mesa PanVK; they must be re-verified
  against the driver fork's tree whenever its pin moves (Phase 4).

## References

- [panvk-kbase-upstream-report.md](panvk-kbase-upstream-report.md): the two kbase/CSF bugs filed
  upstream (fragment-subqueue sync wait; Zink without Kopper), with the diagnosis trail.
- [../../MALI_PORT.md](../../MALI_PORT.md): what this fork ported and what was adapted.
