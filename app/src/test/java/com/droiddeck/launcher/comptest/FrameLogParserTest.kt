package com.droiddeck.launcher.comptest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameLogParserTest {
    @Test fun fpsLinesBecomeFrameTimes() {
        val frames = FrameLogParser.parseFrameTimes(listOf("59.94 fps", "60.0 FPS", "noise"))
        assertEquals(2, frames.size)
        assertEquals(1000.0 / 59.94, frames[0], 1e-9)
        assertEquals(1000.0 / 60.0, frames[1], 1e-9)
    }

    @Test fun frameMsLinesArePickedUp() {
        val frames = FrameLogParser.parseFrameTimes(listOf("frame: 16.7 ms", "frame time 33.3ms"))
        assertEquals(listOf(16.7, 33.3), frames)
    }

    @Test fun msWithoutFrameKeywordIsIgnored() {
        assertTrue(FrameLogParser.parseFrameTimes(listOf("took 5 ms to init")).isEmpty())
    }

    @Test fun garbageYieldsEmpty() {
        assertTrue(FrameLogParser.parseFrameTimes(listOf("", "hello", "fps:")).isEmpty())
    }
}
