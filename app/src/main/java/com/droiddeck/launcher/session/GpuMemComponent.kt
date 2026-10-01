package com.droiddeck.launcher.session

import android.util.Log
import com.droiddeck.launcher.core.SessionPart
import java.io.File

/**
 * The GPU memory in use, for the performance overlay's VRAM and PVRAM lines.
 *
 * Valve's mangoapp reads it from the kernel's gpu_mem_total tracepoint, which no Android app may
 * reach, so both lines read 0. Our libtracefs for mangoapp (tools/mangoapp/libtracefs-shim.c) hands
 * it what this writes to [file] - `/run/droiddeck-hud/gpu-mem` in the guest - every few seconds, in
 * bytes, from the best of what KGSL lets the app read:
 *
 * - `/sys/class/kgsl/kgsl/page_alloc`, every GPU page on the device - what the tracepoint's total
 *   counts, where the policy allows it;
 * - else KGSL's own accounting of each of the session's processes
 *   (`/sys/class/kgsl/kgsl/proc/<pid>/gpumem_mapped` and `gpumem_unmapped`);
 * - else the size of the KGSL mappings in each of the session's processes' `smaps` - only the
 *   buffers the CPU has mapped, so less than the GPU holds (about 40 % of KGSL's own count in a
 *   game on an AYN Thor), but always readable: every process the app can see in `/proc` is one of
 *   its own.
 */
class GpuMemComponent(private val file: File) : SessionPart() {
    @Volatile private var running = false
    private var thread: Thread? = null
    private var source = ""

    /** True when there is a source to write from. */
    fun prepare(): Boolean {
        val bytes = sample() ?: return false
        write(bytes)
        Log.i(TAG, "hud: gpu memory from $source (${bytes / (1024 * 1024)} MiB now)")
        return true
    }

    override fun start() {
        running = true
        thread = Thread({
            while (running) {
                try { Thread.sleep(PERIOD_MS) } catch (e: InterruptedException) { break }
                if (running) sample()?.let { write(it) }
            }
        }, "gpu-mem").apply { isDaemon = true; start() }
    }

    override fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        file.delete()
    }

    private fun sample(): Long? {
        readLong("$KGSL_ROOT/page_alloc")?.let { source = "$KGSL_ROOT/page_alloc"; return it }
        val pids = sessionPids()
        var perProcess = 0L
        var anyProcess = false
        for (pid in pids) {
            val mapped = readLong("$KGSL_ROOT/proc/$pid/gpumem_mapped") ?: continue
            perProcess += mapped + (readLong("$KGSL_ROOT/proc/$pid/gpumem_unmapped") ?: 0L)
            anyProcess = true
        }
        if (anyProcess) { source = "$KGSL_ROOT/proc/<pid>/gpumem_*"; return perProcess }
        var mappedKb = 0L
        var anyMap = false
        for (pid in pids) {
            val kb = kgslMappedKb(pid) ?: continue
            mappedKb += kb
            anyMap = true
        }
        if (anyMap) { source = "/proc/<pid>/smaps (KGSL mappings)"; return mappedKb * 1024 }
        return null
    }

    /** The processes this app may see in /proc: Android hides every other uid's. */
    private fun sessionPids(): List<String> =
        File("/proc").list()?.filter { it.isNotEmpty() && it.all(Char::isDigit) }.orEmpty()

    /** Size of a process's mappings of the KGSL device, in kB; null where its smaps cannot be read. */
    private fun kgslMappedKb(pid: String): Long? = runCatching {
        var total = 0L
        var inKgsl = false
        File("/proc/$pid/smaps").forEachLine { line ->
            if (MAPPING.containsMatchIn(line)) {
                inKgsl = line.contains("kgsl-3d0") || line.contains("/dev/dri/renderD")
            } else if (inKgsl && line.startsWith("Size:")) {
                total += line.substring(5).trim().removeSuffix("kB").trim().toLongOrNull() ?: 0L
            }
        }
        total
    }.getOrNull()

    /** Whole, renamed into place: the reader opens either the last value or this one. */
    private fun write(bytes: Long) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText("$bytes\n")
            if (!tmp.renameTo(file)) tmp.delete()
        } catch (e: Exception) {
        }
    }

    companion object {
        private const val TAG = "SessionService"
        private const val KGSL_ROOT = "/sys/class/kgsl/kgsl"
        private const val PERIOD_MS = 2_000L
        /** A mapping's header in smaps: its address range first, then its fields and path. */
        private val MAPPING = Regex("^[0-9a-f]+-[0-9a-f]+ ")

        private fun readLong(path: String): Long? =
            runCatching { File(path).readText().trim().toLong() }.getOrNull()
    }
}
