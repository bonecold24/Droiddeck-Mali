package com.droiddeck.launcher.comptest

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File

/**
 * Best-effort device telemetry captured around each test.
 *
 * Sysfs thermal zones are read opportunistically — unreadable paths yield null,
 * never an exception. Actual on-device readings are NEEDS-REAL-MALI; the parsing
 * helpers below are pure and host-unit-tested.
 */
object CompatTelemetry {
    /** "45250" (millidegree C) -> 45.25 */
    fun parseMillidegreeC(text: String?): Double? {
        val v = text?.trim()?.toLongOrNull() ?: return null
        return v / 1000.0
    }

    /**
     * Picks (cpuZoneName, gpuZoneName) from thermal-zone name->type pairs.
     * Prefers explicit cpu/gpu/mali labels; falls back to null (not guessed).
     */
    fun pickZones(types: Map<String, String>): Pair<String?, String?> {
        fun find(vararg needles: String): String? =
            types.entries.firstOrNull { (_, t) ->
                val lower = t.lowercase()
                needles.any { lower.contains(it) }
            }?.key
        val gpu = find("gpu", "mali", "gpuss")
        val cpu = find("cpu", "cluster", "big", "little", "soc")
            ?.takeIf { it != gpu }
            ?: types.entries.firstOrNull { (n, _) -> n != gpu }?.key
        return cpu to gpu
    }

    /** Android-only. Returns nulls (not throws) for anything unreadable. */
    fun snapshot(context: Context): TelemetrySnapshot {
        val now = System.currentTimeMillis()
        var cpuC: Double? = null
        var gpuC: Double? = null
        try {
            val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
                .orEmpty()
                .mapNotNull { z ->
                    val type = runCatching { File(z, "type").readText().trim() }.getOrNull()
                    if (type.isNullOrEmpty()) null else z.name to type
                }.toMap()
            val (cpuZone, gpuZone) = pickZones(zones)
            cpuC = cpuZone?.let { parseMillidegreeC(runCatching { File("/sys/class/thermal/$it/temp").readText() }.getOrNull()) }
            gpuC = gpuZone?.let { parseMillidegreeC(runCatching { File("/sys/class/thermal/$it/temp").readText() }.getOrNull()) }
        } catch (_: Exception) { }
        val batteryPct: Int? = try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100 / scale) else null
        } catch (_: Exception) { null }
        val ramUsedMb: Long? = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            (mi.totalMem - mi.availMem) / (1024 * 1024)
        } catch (_: Exception) { null }
        return TelemetrySnapshot(cpuC, gpuC, batteryPct, ramUsedMb, now)
    }

    /** Battery drain in percentage points between two snapshots; null if either is missing. */
    fun batteryDrainPct(start: TelemetrySnapshot?, end: TelemetrySnapshot?): Int? {
        val a = start?.batteryPct
        val b = end?.batteryPct
        return if (a != null && b != null) a - b else null
    }
}
