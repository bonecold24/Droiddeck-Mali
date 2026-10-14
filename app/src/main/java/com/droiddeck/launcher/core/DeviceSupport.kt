package com.droiddeck.launcher.core

import android.content.Context
import android.os.Build
import com.droiddeck.launcher.R
import java.io.File

object DeviceSupport {
    fun adreno(): Boolean =
        File("/sys/class/kgsl/kgsl-3d0").exists() || File("/vendor/lib64/hw/vulkan.adreno.so").exists()

    /**
     * Probe paths that identify an Arm Mali GPU: the kbase kernel node, its sysfs
     * entry, or the vendor's Mali Vulkan userspace driver.
     */
    internal val maliProbePaths = listOf(
        "/dev/mali0",
        "/sys/class/misc/mali0",
        "/vendor/lib64/hw/vulkan.mali.so",
        "/vendor/lib/hw/vulkan.mali.so",
    )

    fun mali(): Boolean = maliProbePaths.any { File(it).exists() }

    fun gpuName(context: Context): String {
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } else null
        return soc?.let { "$it (${Build.HARDWARE})" } ?: Build.HARDWARE.ifBlank { context.getString(R.string.gpu_this_gpu) }
    }

    fun lowLatencyWifiSafe(): Boolean = Build.VERSION.SDK_INT < 31 || Build.SOC_MODEL != "CQ8725S"
}
