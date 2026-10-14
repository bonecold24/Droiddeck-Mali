package com.droiddeck.launcher.core

import org.junit.Assert.*
import org.junit.Test

class LogTailBufferTest {
    @Test fun keepsTheLastLinesUpToCapacity() {
        val buf = LogTailBuffer(3)
        buf.add("a")
        buf.add("b")
        assertEquals(listOf("a", "b"), buf.tail(10))
        buf.add("c")
        buf.add("d")
        assertEquals(listOf("b", "c", "d"), buf.tail(10))
    }

    @Test fun tailRespectsMax() {
        val buf = LogTailBuffer(10)
        repeat(5) { buf.add("l$it") }
        assertEquals(listOf("l3", "l4"), buf.tail(2))
        assertEquals(5, buf.tail(100).size)
    }

    @Test fun emptyBufferYieldsEmptyTail() {
        assertTrue(LogTailBuffer(10).tail(5).isEmpty())
    }

    @Test fun clearEmptiesTheBuffer() {
        val buf = LogTailBuffer(10)
        buf.add("x")
        buf.clear()
        assertTrue(buf.tail(10).isEmpty())
    }

    @Test fun zeroOrNegativeMaxYieldsEmpty() {
        val buf = LogTailBuffer(10)
        buf.add("x")
        assertTrue(buf.tail(0).isEmpty())
        assertTrue(buf.tail(-1).isEmpty())
    }
}
