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
 * there instead: `gpu_busy_percentage` worked out from `gpubusy` (busy and total cycles of the
 * last sample) and `clock_mhz` from `gpuclk` - files with labels of their own that policies often
 * leave to apps for GPU profilers - when they are readable, and `temp` linked to the GPU's
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
            val busy = File(KGSL, "gpubusy").canRead()
            if (busy) writeLoad(0)
            val clk = File(KGSL, "gpuclk").canRead()
            if (clk) readClock()?.let { write(clock, String.format("%5d\n", it)) }
            val temp = LinuxRuntime.gpuTempSource()
            if (temp != null) Os.symlink(temp, File(dir, "temp").path)
            Log.i(TAG, "hud: kgsl stats from the session's own: load " + (if (busy) "from gpubusy" else "none") +
                ", clock " + (if (clk) "from gpuclk" else "none") + ", temp " + (temp ?: "none"))
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

    private fun readBusy(): Int? = try {
        val parts = File(KGSL, "gpubusy").readText().trim().split(Regex("\\s+"))
        val busy = parts[0].toLong()
        val total = parts[1].toLong()
        if (total > 0) (busy * 100 / total).toInt().coerceIn(0, 100) else 0
    } catch (e: Exception) {
        null
    }

    /** The GPU clock in MHz; gpuclk is in Hz. */
    private fun readClock(): Int? = try {
        (File(KGSL, "gpuclk").readText().trim().toLong() / 1_000_000).toInt()
    } catch (e: Exception) {
        null
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
        private const val PERIOD_MS = 500L
    }
}
