/*
 * drm-shim.c — LD_PRELOAD shim for Gamescope on Mali (DroidDeck B1).
 *
 * Problem: Upstream's prebuilt libvulkan_panfrost.so lacks DroidDeck's rdev
 * patch (tools/panvk/apply-droiddeck-patches.py). On an Android mali_kbase
 * kernel, /dev/mali0 is a misc device, not /dev/dri/card0. Without the patch,
 * VK_EXT_physical_device_drm reports hasPrimary=false, hasRender=false, and
 * Gamescope's headless backend fails with "physical device has no primary node".
 *
 * Why interpose vkGetInstanceProcAddr: Gamescope never calls the global
 * vkGetPhysicalDeviceProperties2 symbol — it resolves every entry point
 * through vkGetInstanceProcAddr, which dispatches via the loader's internal
 * table and bypasses LD_PRELOAD symbol interposition entirely. (On-device
 * proof: the v1 shim's constructor logged
 * "drm-shim: dlsym vkGetPhysicalDeviceProperties2 failed".)
 *
 * Solution: export our own vkGetInstanceProcAddr. It delegates to the real
 * one, but when the caller asks for "vkGetPhysicalDeviceProperties2" we
 * return our wrapper instead. The wrapper calls the real function, then
 * walks the pNext chain for VkPhysicalDeviceDrmPropertiesEXT; if
 * hasPrimary==false we stat /dev/mali0, extract st_rdev, and fill
 * primary/render major/minor. This mirrors what the rdev patch does at
 * driver build time, without recompiling Mesa or binary-patching the .so.
 *
 * Build (aarch64 glibc):
 *   aarch64-linux-gnu-gcc -O2 -shared -fPIC -o drm-shim.so \
 *       tools/comptest/drm-shim.c -ldl
 *
 * Use:
 *   LD_PRELOAD=/opt/comptest/drm-shim.so gamescope --backend headless ...
 *
 * License: MIT.
 */

#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <vulkan/vulkan.h>

typedef void (*PFN_WrappedGetProps2)(VkPhysicalDevice,
                                     VkPhysicalDeviceProperties2 *);

static PFN_vkGetInstanceProcAddr real_gipa = NULL;
/* Real vkGetPhysicalDeviceProperties2 captured when vkGetInstanceProcAddr
 * is asked for it (per instance); plus a dlsym fallback for NULL instance. */
static PFN_WrappedGetProps2 real_getProps2 = NULL;
static PFN_WrappedGetProps2 real_getProps2KHR = NULL;
static int logged_no_dev = 0;

static void fill_drm_props(VkPhysicalDeviceProperties2 *pProperties)
{
    void *next = pProperties->pNext;
    while (next) {
        VkBaseOutStructure *base = (VkBaseOutStructure *)next;
        if (base->sType ==
            VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRM_PROPERTIES_EXT) {
            VkPhysicalDeviceDrmPropertiesEXT *drm =
                (VkPhysicalDeviceDrmPropertiesEXT *)next;
            if (!drm->hasPrimary) {
                struct stat st;
                /* Try /dev/mali0 first (Android kbase), then /dev/dri/card0 */
                const char *paths[] = { "/dev/mali0", "/dev/dri/card0", NULL };
                for (int i = 0; paths[i]; i++) {
                    if (stat(paths[i], &st) == 0 && S_ISCHR(st.st_mode)) {
                        uint32_t maj = major(st.st_rdev);
                        uint32_t min = minor(st.st_rdev);
                        drm->hasPrimary = VK_TRUE;
                        drm->primaryMajor = maj;
                        drm->primaryMinor = min;
                        drm->hasRender = VK_TRUE;
                        drm->renderMajor = maj;
                        drm->renderMinor = min;
                        fprintf(stderr,
                            "drm-shim: filled DRM props from %s (major=%u minor=%u)\n",
                            paths[i], maj, min);
                        break;
                    }
                }
                if (!drm->hasPrimary && !logged_no_dev) {
                    logged_no_dev = 1;
                    fprintf(stderr,
                        "drm-shim: no DRM device found, leaving empty\n");
                }
            }
            break;
        }
        next = base->pNext;
    }
}

static void VKAPI_CALL wrapped_vkGetPhysicalDeviceProperties2(
    VkPhysicalDevice physicalDevice,
    VkPhysicalDeviceProperties2 *pProperties)
{
    if (!real_getProps2) {
        /* Last resort: direct symbol (expected to fail on this loader). */
        real_getProps2 =
            (PFN_WrappedGetProps2)dlsym(RTLD_NEXT,
                                        "vkGetPhysicalDeviceProperties2");
        if (!real_getProps2) {
            fprintf(stderr,
                "drm-shim: no real vkGetPhysicalDeviceProperties2 available\n");
            return;
        }
    }
    real_getProps2(physicalDevice, pProperties);
    fill_drm_props(pProperties);
}

static void VKAPI_CALL wrapped_vkGetPhysicalDeviceProperties2KHR(
    VkPhysicalDevice physicalDevice,
    VkPhysicalDeviceProperties2 *pProperties)
{
    if (!real_getProps2KHR) {
        real_getProps2KHR =
            (PFN_WrappedGetProps2)dlsym(RTLD_NEXT,
                                        "vkGetPhysicalDeviceProperties2KHR");
        if (!real_getProps2KHR) {
            fprintf(stderr,
                "drm-shim: no real vkGetPhysicalDeviceProperties2KHR available\n");
            return;
        }
    }
    real_getProps2KHR(physicalDevice, pProperties);
    fill_drm_props(pProperties);
}

__attribute__((constructor))
static void init(void)
{
    fprintf(stderr, "drm-shim: loaded (interposing vkGetInstanceProcAddr)\n");
}

/*
 * Interposed loader entry point. Delegates everything to the real
 * vkGetInstanceProcAddr except vkGetPhysicalDeviceProperties2, for which
 * we return our wrapper (capturing the real function pointer so the
 * wrapper can call through).
 */
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetInstanceProcAddr(
    VkInstance instance,
    const char *pName)
{
    if (!real_gipa) {
        real_gipa =
            (PFN_vkGetInstanceProcAddr)dlsym(RTLD_NEXT,
                                             "vkGetInstanceProcAddr");
        if (!real_gipa) {
            fprintf(stderr,
                "drm-shim: dlsym vkGetInstanceProcAddr failed\n");
            return NULL;
        }
    }
    PFN_vkVoidFunction fn = real_gipa(instance, pName);
    if (pName && strcmp(pName, "vkGetPhysicalDeviceProperties2") == 0) {
        if (fn) {
            real_getProps2 = (PFN_WrappedGetProps2)fn;
            fprintf(stderr,
                "drm-shim: intercepting vkGetPhysicalDeviceProperties2\n");
            return (PFN_vkVoidFunction)wrapped_vkGetPhysicalDeviceProperties2;
        }
        /* instance==NULL or unknown instance: nothing to wrap. */
        return fn;
    }
    if (pName && strcmp(pName, "vkGetPhysicalDeviceProperties2KHR") == 0) {
        if (fn) {
            real_getProps2KHR = (PFN_WrappedGetProps2)fn;
            fprintf(stderr,
                "drm-shim: intercepting vkGetPhysicalDeviceProperties2KHR\n");
            return (PFN_vkVoidFunction)wrapped_vkGetPhysicalDeviceProperties2KHR;
        }
        return fn;
    }
    return fn;
}

/*
 * vk_icdGetInstanceProcAddr: the ICD-level entry point. Some loaders/drivers
 * route through this instead of vkGetInstanceProcAddr. Apply the same
 * interception here for robustness.
 */
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vk_icdGetInstanceProcAddr(
    VkInstance instance,
    const char *pName)
{
    /* Delegate to our vkGetInstanceProcAddr wrapper, which does the real work. */
    return vkGetInstanceProcAddr(instance, pName);
}

/*
 * Also export the direct symbol for any caller that links against it
 * (harmless if the loader does not export it; our constructor path is
 * the vkGetInstanceProcAddr one above).
 */
VKAPI_ATTR void VKAPI_CALL vkGetPhysicalDeviceProperties2(
    VkPhysicalDevice physicalDevice,
    VkPhysicalDeviceProperties2 *pProperties)
{
    wrapped_vkGetPhysicalDeviceProperties2(physicalDevice, pProperties);
}

VKAPI_ATTR void VKAPI_CALL vkGetPhysicalDeviceProperties2KHR(
    VkPhysicalDevice physicalDevice,
    VkPhysicalDeviceProperties2 *pProperties)
{
    wrapped_vkGetPhysicalDeviceProperties2KHR(physicalDevice, pProperties);
}

/*
 * drmGetDeviceFromDevId interposition: Gamescope may call libdrm's
 * drmGetDeviceFromDevId(makedev(primaryMajor, primaryMinor)) after reading
 * VkPhysicalDeviceDrmPropertiesEXT. libdrm rejects devices whose major is
 * not DRM_MAJOR (226) or that lack /sys/dev/char/.../device/drm. On Android
 * kbase, /dev/mali0 is major=10 (misc), so libdrm would fail. We interpose
 * and return success (0) for the mali device, letting Gamescope proceed
 * with the DRM properties we already filled.
 *
 * Signature: int drmGetDeviceFromDevId(dev_t dev_id, uint32_t flags,
 *                                      drmDevicePtr *device)
 * We return 0 without populating *device (Gamescope only checks the return
 * value for the primary-node validation path). If the real libdrm can handle
 * it (e.g., a real /dev/dri/card0), delegate to the real function.
 */
int drmGetDeviceFromDevId(unsigned long long dev_id, uint32_t flags,
                           void **device)
{
    static int (*real_drmGetDeviceFromDevId)(unsigned long long, uint32_t,
                                             void **) = NULL;
    if (!real_drmGetDeviceFromDevId) {
        real_drmGetDeviceFromDevId =
            dlsym(RTLD_NEXT, "drmGetDeviceFromDevId");
    }

    /* Check if this is our mali device (major=10) */
    unsigned int maj = (unsigned int)(dev_id >> 8) & 0xfff;
    /* Actually use gnu_dev_makedev decoding: major is bits 8-19 */
    maj = ((dev_id >> 8) & 0xfff) | ((dev_id >> 32) & ~0xfff);

    struct stat st;
    int is_mali = 0;
    if (stat("/dev/mali0", &st) == 0 && st.st_rdev == (dev_t)dev_id) {
        is_mali = 1;
    }

    if (is_mali) {
        fprintf(stderr,
            "drm-shim: drmGetDeviceFromDevId intercepted for /dev/mali0, returning success\n");
        if (device) *device = NULL;
        return 0;
    }

    if (real_drmGetDeviceFromDevId) {
        return real_drmGetDeviceFromDevId(dev_id, flags, device);
    }
    return -1;
}
