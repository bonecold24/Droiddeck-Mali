package com.droiddeck.launcher.session

import android.system.Os
import android.util.Log
import com.droiddeck.launcher.core.SessionPart
import com.droiddeck.launcher.runtime.LinuxRuntime
import java.io.File
import java.io.RandomAccessFile

/**
 * The Adreno's stats as mangoapp (Deck mode's performance overlay) reads them.
 *
 * MangoHud takes an Adreno's load and temperature from `/sys/class/kgsl/kgsl-3d0` and ends the
 * process on a file there it may not read - not a file that is missing, which it skips. Under an
 * enforcing SELinux policy (every retail phone) KGSL's sysfs is refused to apps, so mangoapp died
 * on start and the session ran with no overlay. Where that is so, a directory of our own is bound
 * there instead: `gpu_busy_percentage` and `clock_mhz` from Qualcomm's `/sys/kernel/gpu` (load
 * and MHz, under the plain sysfs label - where other Android overlays read them), else from KGSL's
 * `gpubusy` (busy and total cycles of the last sample) and `gpuclk` (Hz), files with labels of
 * their own that policies often leave to apps for GPU profilers; and `temp` linked to the GPU's
 * thermal zone when there is one. What is not readable is left out, and the overlay shows no line
 * for it. Where KGSL's own files are readable, nothing is done.
 */
class GpuStatsComponent(val dir: File) : SessionPart() {
    @Volatile private var running = false
    private var thread: Thread? = null
    private val load = File(dir, "gpu_busy_percentage")
    private val clock = File(dir, "clock_mhz")

    /** Writes the directory; true when it should be bound over [KGSL]. */
    fun prepare(): Boolean {
        if (!File(KGSL).exists() || File(KGSL, "gpu_busy_percentage").canRead()) return false
        return try {
            dir.mkdirs()
            dir.listFiles()?.forEach { java.nio.file.Files.deleteIfExists(it.toPath()) }
            val busy = readBusy()?.also { writeLoad(it) } != null
            val clk = readClock()?.also { write(clock, String.format("%5d\n", it)) } != null
            val temp = LinuxRuntime.gpuTempSource()
            if (temp != null) Os.symlink(temp, File(dir, "temp").path)
            Log.i(TAG, "hud: kgsl stats from the session's own: load " + (if (busy) loadFrom else "none") +
                ", clock " + (if (clk) clockFrom else "none") + ", temp " + (temp ?: "none"))
            true
        } catch (e: Exception) {
            Log.w(TAG, "hud: could not stand in for kgsl stats: $e")
            false
        }
    }

    override fun start() {
        if (!load.exists() && !clock.exists()) return
        running = true
        thread = Thread({
            while (running) {
                try { Thread.sleep(PERIOD_MS) } catch (e: InterruptedException) { break }
                if (!running) break
                if (load.exists()) readBusy()?.let { writeLoad(it) }
                if (clock.exists()) readClock()?.let { write(clock, String.format("%5d\n", it)) }
            }
        }, "gpu-stats").apply { isDaemon = true; start() }
    }

    override fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private var loadFrom = ""
    private var clockFrom = ""

    /** GPU load in percent: /sys/kernel/gpu's "NN %", else KGSL's busy and total cycles. */
    private fun readBusy(): Int? {
        runCatching { File(QCOM_GPU, "gpu_busy").readText().trim().removeSuffix("%").trim().toInt() }.getOrNull()?.let {
            loadFrom = "$QCOM_GPU/gpu_busy"
            return it.coerceIn(0, 100)
        }
        return runCatching {
            val parts = File(KGSL, "gpubusy").readText().trim().split(Regex("\\s+"))
            val total = parts[1].toLong()
            if (total > 0) (parts[0].toLong() * 100 / total).toInt().coerceIn(0, 100) else 0
        }.getOrNull()?.also { loadFrom = "$KGSL/gpubusy" }
    }

    /** GPU clock in MHz: /sys/kernel/gpu's, else KGSL's gpuclk in Hz. */
    private fun readClock(): Int? {
        runCatching { File(QCOM_GPU, "gpu_clock").readText().trim().toInt() }.getOrNull()?.let {
            clockFrom = "$QCOM_GPU/gpu_clock"
            return it
        }
        return runCatching { (File(KGSL, "gpuclk").readText().trim().toLong() / 1_000_000).toInt() }.getOrNull()
            ?.also { clockFrom = "$KGSL/gpuclk" }
    }

    private fun writeLoad(percent: Int) = write(load, String.format("%3d %%\n", percent))

    /** In place and at one length, so a reader that keeps the file open sees each value whole. */
    private fun write(file: File, text: String) {
        try {
            RandomAccessFile(file, "rw").use { it.seek(0); it.write(text.toByteArray()) }
        } catch (e: Exception) {
        }
    }

    companion object {
        private const val TAG = "SessionService"
        const val KGSL = "/sys/class/kgsl/kgsl-3d0"
        /** Qualcomm's own GPU summary, beside KGSL in vendor kernels. */
        private const val QCOM_GPU = "/sys/kernel/gpu"
        private const val PERIOD_MS = 500L
    }
}
