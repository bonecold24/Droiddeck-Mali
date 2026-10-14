package com.droiddeck.launcher.comptest

import kotlin.math.roundToInt

/** FPS / frame-time statistics from a per-frame time series (milliseconds). Pure math. */
data class FpsStats(
    val avgFps: Double,
    val onePercentLowFps: Double,
    val maxFrameTimeMs: Double,
)

object FpsStats {
    /**
     * Computes stats from frame times in ms.
     * - avgFps: 1000 / mean(frame time)
     * - onePercentLowFps: 1000 / mean(worst 1% of frames), min 1 frame
     * - maxFrameTimeMs: worst single frame
     * Returns null when no usable samples exist.
     */
    fun compute(frameTimesMs: List<Double>): FpsStats? {
        val clean = frameTimesMs.filter { it > 0.0 && it.isFinite() }
        if (clean.isEmpty()) return null
        val mean = clean.sum() / clean.size
        val worstCount = (clean.size / 100.0).roundToInt().coerceAtLeast(1)
        val worst = clean.sortedDescending().take(worstCount)
        val worstMean = worst.sum() / worst.size
        return FpsStats(
            avgFps = 1000.0 / mean,
            onePercentLowFps = 1000.0 / worstMean,
            maxFrameTimeMs = clean.max(),
        )
    }
}
