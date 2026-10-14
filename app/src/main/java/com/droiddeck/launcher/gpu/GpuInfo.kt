package com.droiddeck.launcher.gpu

import android.content.Context
import android.os.Build
import com.droiddeck.launcher.R
import com.droiddeck.launcher.core.DeviceSupport
import com.droiddeck.launcher.core.FileUtils
import java.io.File

data class GpuInfo(
    val name: String,
    val model: Int,
    val family: Family,
    val soc: String,
    val oneUi8Gen2: Boolean,
    val kgslName: String = "",
    val modelSource: String = "",
) {
    enum class Family(val label: String) {
        A8XX("Adreno 8xx"),
        A7XX_LOW("Adreno 710/720/722"),
        A7XX("Adreno 7xx"),
        A6XX("Adreno 6xx"),
        ADRENO_UNKNOWN("Adreno"),
        MALI("Arm Mali"),
        NOT_ADRENO("Not an Adreno GPU");

        fun label(context: Context): String = if (this == NOT_ADRENO) context.getString(R.string.gpuinfo_not_adreno) else label
    }

    enum class Support { TESTED, UNTESTED, UNSUPPORTED }

    val support: Support
        get() = when {
            family == Family.NOT_ADRENO -> Support.UNSUPPORTED
            family == Family.MALI -> Support.UNTESTED
            family == Family.A6XX && model == 650 -> Support.TESTED
            family == Family.A8XX -> Support.TESTED
            family == Family.A7XX && model >= 725 -> Support.TESTED
            else -> Support.UNTESTED
        }

    val supportText: String
        get() = when (support) {
            Support.TESTED -> "Supported"
            Support.UNTESTED -> when {
                family == Family.A7XX_LOW -> "Experimental: its drivers are test builds"
                family == Family.MALI -> "Experimental: Mali needs an imported Linux runtime driver (PanVK for kbase)"
                else -> "Outside tested hardware (Adreno 650, 725 and newer): it may not run"
            }
            Support.UNSUPPORTED -> "Not supported: DroidDeck needs an Adreno (Snapdragon) GPU"
        }

    fun supportText(context: Context): String = context.getString(when (support) {
        Support.TESTED -> R.string.gpuinfo_supported
        Support.UNTESTED -> if (family == Family.A7XX_LOW) R.string.gpuinfo_experimental else R.string.gpuinfo_below_tested
        Support.UNSUPPORTED -> R.string.gpuinfo_unsupported
    })

    fun displayName(context: Context): String = if (name == UNNAMED) context.getString(R.string.gpu_this_gpu) else name

    companion object {
        private const val UNNAMED = "this GPU"

        fun detect(): GpuInfo {
            val adreno = File("/sys/class/kgsl/kgsl-3d0").exists() || File("/vendor/lib64/hw/vulkan.adreno.so").exists()
            val raw = listOf("/sys/class/kgsl/kgsl-3d0/gpu_model", "/sys/class/kgsl/kgsl-3d0/gpu_chipid")
                .firstNotNullOfOrNull { FileUtils.readString(File(it))?.trim()?.takeIf(String::isNotEmpty) }
            val soc = if (adreno) SocNames.label() else maliSoc()
            if (!adreno && DeviceSupport.mali()) return detectMali(soc)
            val fromKernel = raw?.let { threeDigits(it) } ?: 0
            val fromVulkan = if (fromKernel > 0 || !adreno) 0 else VulkanInfo.cachedOrNull()?.get("device")?.let { threeDigits(it) } ?: 0
            val fromPlatform = if (fromKernel > 0 || fromVulkan > 0 || !adreno) 0 else platformModel()
            val model = maxOf(fromKernel, fromVulkan, fromPlatform)
            val source = when {
                fromKernel > 0 -> "kernel"
                fromVulkan > 0 -> "vulkan"
                fromPlatform > 0 -> "platform"
                else -> ""
            }
            val family = familyOf(adreno, model)
            val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
            return GpuInfo(
                name = if (!adreno) Build.HARDWARE.ifBlank { UNNAMED } else if (model > 0) "Adreno $model" else "Adreno",
                model = model, family = family, soc = soc,
                oneUi8Gen2 = samsung && model == 740,
                kgslName = raw.orEmpty(), modelSource = source,
            )
        }

        private fun threeDigits(text: String): Int = Regex("""(\d{3})""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        /** The SoC on a non-Adreno device, where the Adreno tables in SocNames do not apply. */
        private fun maliSoc(): String =
            if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }.orEmpty() else ""

        /**
         * A Mali: kbase's gpuinfo names it ("Mali-G710 10 cores r0p0 0xA862"), where the kernel lets
         * the app read it - most retail kernels do not - else the GL driver's renderer string
         * ("Mali-G720 MC12"); otherwise the SoC stands in. model is the number after the G ("720").
         */
        private fun detectMali(soc: String): GpuInfo {
            val raw = listOf("/sys/class/misc/mali0/device/gpuinfo", "/sys/devices/platform/mali/gpuinfo")
                .firstNotNullOfOrNull { FileUtils.readString(File(it))?.trim()?.takeIf(String::isNotEmpty) }
            val name = (raw ?: glRenderer())?.let { Regex("""Mali-[A-Z]?\d+( MC\d+)?""").find(it)?.value }
            val model = name?.let { Regex("""(\d+)""").find(it)?.value?.toIntOrNull() } ?: 0
            return GpuInfo(
                name = name ?: "Mali (${soc.ifEmpty { Build.HARDWARE.ifBlank { "unknown SoC" } }})",
                model = model, family = Family.MALI, soc = soc, oneUi8Gen2 = false,
                kgslName = raw.orEmpty(), modelSource = if (raw != null) "kbase" else if (name != null) "gl" else "",
            )
        }

        @Volatile private var renderer: String? = null
        @Volatile private var rendererRead = false

        /**
         * GL_RENDERER from a throwaway 1x1 pbuffer context, once per process: the one name for the
         * GPU an app may always read. Null when EGL refuses.
         */
        @Synchronized
        private fun glRenderer(): String? {
            if (rendererRead) return renderer
            rendererRead = true
            renderer = runCatching {
                val display = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)
                val version = IntArray(2)
                if (!android.opengl.EGL14.eglInitialize(display, version, 0, version, 1)) return@runCatching null
                val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
                val count = IntArray(1)
                android.opengl.EGL14.eglChooseConfig(display, intArrayOf(
                    android.opengl.EGL14.EGL_RENDERABLE_TYPE, android.opengl.EGL14.EGL_OPENGL_ES2_BIT,
                    android.opengl.EGL14.EGL_SURFACE_TYPE, android.opengl.EGL14.EGL_PBUFFER_BIT,
                    android.opengl.EGL14.EGL_NONE), 0, configs, 0, 1, count, 0)
                val config = configs[0] ?: return@runCatching null
                val context = android.opengl.EGL14.eglCreateContext(display, config, android.opengl.EGL14.EGL_NO_CONTEXT,
                    intArrayOf(android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, android.opengl.EGL14.EGL_NONE), 0)
                val surface = android.opengl.EGL14.eglCreatePbufferSurface(display, config,
                    intArrayOf(android.opengl.EGL14.EGL_WIDTH, 1, android.opengl.EGL14.EGL_HEIGHT, 1, android.opengl.EGL14.EGL_NONE), 0)
                try {
                    if (!android.opengl.EGL14.eglMakeCurrent(display, surface, surface, context)) null
                    else android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER)
                } finally {
                    android.opengl.EGL14.eglMakeCurrent(display, android.opengl.EGL14.EGL_NO_SURFACE,
                        android.opengl.EGL14.EGL_NO_SURFACE, android.opengl.EGL14.EGL_NO_CONTEXT)
                    android.opengl.EGL14.eglDestroySurface(display, surface)
                    android.opengl.EGL14.eglDestroyContext(display, context)
                }
            }.getOrNull()
            return renderer
        }

        private val PLATFORMS = mapOf(
            "msmnile" to 640, "kona" to 650, "lahaina" to 660, "taro" to 730, "cape" to 730,
            "kalama" to 740, "pineapple" to 750, "sun" to 830,
        )

        internal fun platformModel(platform: String = systemProperty("ro.board.platform")): Int =
            PLATFORMS[platform.lowercase()] ?: PLATFORMS[systemProperty("ro.vendor.qti.soc_name").lowercase()] ?: 0

        fun systemProperty(name: String): String = try {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as? String ?: ""
        } catch (_: Exception) {
            ""
        }

        internal fun familyOf(adreno: Boolean, model: Int): Family = when {
            !adreno -> Family.NOT_ADRENO
            model == 0 -> Family.ADRENO_UNKNOWN
            model >= 800 -> Family.A8XX
            model in listOf(710, 720, 722) -> Family.A7XX_LOW
            model >= 700 -> Family.A7XX
            model >= 600 -> Family.A6XX
            else -> Family.ADRENO_UNKNOWN
        }
    }
}
