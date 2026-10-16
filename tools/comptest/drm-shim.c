/*
 * drm-shim.c — LD_PRELOAD shim for Gamescope on Mali (DroidDeck B1).
 *
 * Problem: Upstream's prebuilt libvulkan_panfrost.so lacks DroidDeck's rdev
 * patch (tools/panvk/apply-droiddeck-patches.py). On an Android mali_kbase
 * kernel, /dev/mali0 is a misc device, not /dev/dri/card0. Without the patch,
 * VK_EXT_physical_device_drm reports hasPrimary=false, hasRender=false, and
 * Gamescope's headless backend fails with "physical device has no primary node".
 *
 * Solution: Interpose vkGetPhysicalDeviceProperties2. When the pNext chain
 * contains VkPhysicalDeviceDrmPropertiesEXT with hasPrimary==false, stat
 * /dev/mali0, extract st_rdev, and fill primaryMajor/Minor and
 * renderMajor/Minor. This mirrors what the rdev patch does at driver build
 * time, without recompiling Mesa or binary-patching the .so.
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

/* VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRM_PROPERTIES_EXT = 1000158000 */
#define DRM_PROPS_STYPE 1000158000u

typedef struct {
    VkStructureType sType;
    void *pNext;
    VkBool32 hasPrimary;
    VkBool32 hasRender;
    uint32_t primaryMajor;
    uint32_t primaryMinor;
    uint32_t renderMajor;
    uint32_t renderMinor;
} DrmProps;

typedef void (*PFN_GetProps2)(VkPhysicalDevice, VkPhysicalDeviceProperties2 *);

static PFN_GetProps2 real_getProps2 = NULL;

__attribute__((constructor))
static void init(void) {
    real_getProps2 = (PFN_GetProps2)dlsym(RTLD_NEXT, "vkGetPhysicalDeviceProperties2");
    if (!real_getProps2) {
        fprintf(stderr, "drm-shim: dlsym vkGetPhysicalDeviceProperties2 failed\n");
    }
}

void vkGetPhysicalDeviceProperties2(
    VkPhysicalDevice physicalDevice,
    VkPhysicalDeviceProperties2 *pProperties)
{
    if (!real_getProps2) {
        init();
        if (!real_getProps2) return;
    }
    real_getProps2(physicalDevice, pProperties);

    /* Walk pNext for VkPhysicalDeviceDrmPropertiesEXT */
    void *next = pProperties->pNext;
    while (next) {
        uint32_t sType = *(uint32_t *)next;
        if (sType == DRM_PROPS_STYPE) {
            DrmProps *drm = (DrmProps *)next;
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
                if (!drm->hasPrimary) {
                    fprintf(stderr, "drm-shim: no DRM device found, leaving empty\n");
                }
            }
            break;
        }
        next = *(void **)((char *)next + sizeof(uint32_t));
    }
}
