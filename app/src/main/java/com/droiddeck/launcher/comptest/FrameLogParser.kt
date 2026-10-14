package com.droiddeck.launcher.comptest

/**
 * Parses frame-time samples out of vkcube / DXVK-HUD style log lines.
 * Pure — host-unit-tested.
 */
object FrameLogParser {
    private val fpsRe = Regex("""(\d+(?:\.\d+)?)\s*fps\b""", RegexOption.IGNORE_CASE)
    private val msRe = Regex("""(\d+(?:\.\d+)?)\s*ms\b""", RegexOption.IGNORE_CASE)

    fun parseFrameTimes(lines: List<String>): List<Double> {
        val out = ArrayList<Double>()
        for (line in lines) {
            val fps = fpsRe.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
            if (fps != null && fps > 0) {
                out.add(1000.0 / fps)
                continue
            }
            if (line.contains("frame", ignoreCase = true)) {
                msRe.find(line)?.groupValues?.get(1)?.toDoubleOrNull()?.let { out.add(it) }
            }
        }
        return out
    }
}
