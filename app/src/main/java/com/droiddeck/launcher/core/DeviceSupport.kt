package com.droiddeck.launcher.core

import android.content.Context
import android.os.Build
import com.droiddeck.launcher.R
import java.io.File

object DeviceSupport {
    fun adreno(): Boolean =
        File("/sys/class/kgsl/kgsl-3d0").exists() || File("/vendor/lib64/hw/vulkan.adreno.so").exists()

    /** The kbase device node of a Mali GPU; Android names it mali0 on every vendor kernel. */
    const val MALI_DEVICE = "/dev/mali0"

    /**
     * A Mali GPU (MediaTek Dimensity/Helio, Exynos before Xclipse, Tensor): recognised by the
     * kbase node every Mali kernel creates, or the vendor's Mali userspace. Experimental: the
     * compositor runs on the system's Mali Vulkan driver, and the runtime needs an imported
     * glibc Mali driver (PanVK built for the kbase kernel interface).
     */
    @JvmStatic
    fun mali(): Boolean = !adreno() && (
        File(MALI_DEVICE).exists() || File("/sys/class/misc/mali0").exists() ||
            File("/vendor/lib64/hw/vulkan.mali.so").exists() || File("/vendor/lib64/egl/libGLES_mali.so").exists() ||
            File("/vendor/lib64/egl/mali/libGLES_mali.so").exists())

    /** A MediaTek SoC ("mt6983", "MT6989"), whatever its GPU: for the device card and the report. */
    fun mediatek(): Boolean {
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else ""
        return soc.equals("Mediatek", ignoreCase = true) || Build.HARDWARE.lowercase().startsWith("mt")
    }

    /** A GPU a session can at least try to draw on: Adreno, or Mali through an imported driver. */
    fun canTry(): Boolean = adreno() || mali()

    fun gpuName(context: Context): String {
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } else null
        return soc?.let { "$it (${Build.HARDWARE})" } ?: Build.HARDWARE.ifBlank { context.getString(R.string.gpu_this_gpu) }
    }

    fun lowLatencyWifiSafe(): Boolean = Build.VERSION.SDK_INT < 31 || Build.SOC_MODEL != "CQ8725S"
}
