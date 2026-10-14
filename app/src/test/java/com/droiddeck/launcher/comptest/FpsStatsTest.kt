package com.droiddeck.launcher.comptest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

class FpsStatsTest {
    private fun close(a: Double, b: Double, eps: Double = 1e-6) = abs(a - b) < eps

    @Test fun emptySeriesYieldsNull() {
        assertNull(FpsStats.compute(emptyList()))
    }

    @Test fun blankAndNonPositiveFramesAreDropped() {
        assertNull(FpsStats.compute(listOf(0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY)))
    }

    @Test fun steady60fpsSeries() {
        val frames = List(600) { 1000.0 / 60.0 }
        val s = FpsStats.compute(frames)!!
        assert(close(s.avgFps, 60.0))
        assert(close(s.onePercentLowFps, 60.0))
        assert(close(s.maxFrameTimeMs, 1000.0 / 60.0))
    }

    @Test fun onePercentLowUsesWorstOnePercent() {
        // 99 frames at 60fps + 1 frame stutter of 100ms => worst 1% = the stutter frame
        val frames = List(99) { 1000.0 / 60.0 } + listOf(100.0)
        val s = FpsStats.compute(frames)!!
        assert(close(s.maxFrameTimeMs, 100.0))
        assert(close(s.onePercentLowFps, 10.0)) { "1% low should be 10 fps, was ${s.onePercentLowFps}" }
        // avg fps over all 100 frames
        val mean = (99 * (1000.0 / 60.0) + 100.0) / 100.0
        assert(close(s.avgFps, 1000.0 / mean))
    }

    @Test fun onePercentLowAveragesWorstFramesOnLargeSeries() {
        // 1000 frames: 990 at 16.666ms, 10 at 50ms -> worst 1% = ten 50ms frames => 20 fps
        val frames = List(990) { 1000.0 / 60.0 } + List(10) { 50.0 }
        val s = FpsStats.compute(frames)!!
        assert(close(s.onePercentLowFps, 20.0)) { "was ${s.onePercentLowFps}" }
        assert(s.avgFps > 20.0)
    }

    @Test fun singleFrameSeries() {
        val s = FpsStats.compute(listOf(25.0))!!
        assert(close(s.avgFps, 40.0))
        assert(close(s.onePercentLowFps, 40.0))
        assert(close(s.maxFrameTimeMs, 25.0))
    }
}
