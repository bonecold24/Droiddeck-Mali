package com.droiddeck.launcher.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 6: PadState is the pad's button/axis state that gamepad events land in.
 * Pins the button-index constants, press/isDown bit semantics, hat direction
 * and clear/copyFrom.
 */
class PadStateTest {

    @Test fun buttonIndicesMatchTheX360Layout() {
        assertEquals(0, PadState.A)
        assertEquals(1, PadState.B)
        assertEquals(2, PadState.X)
        assertEquals(3, PadState.Y)
        assertEquals(4, PadState.LB)
        assertEquals(5, PadState.RB)
        assertEquals(6, PadState.SELECT)
        assertEquals(7, PadState.START)
        assertEquals(8, PadState.L3)
        assertEquals(9, PadState.R3)
        assertEquals(12, PadState.GUIDE)
        assertEquals(13, PadState.QAM)
    }

    @Test fun pressSetsAndClearsBitsIndependently() {
        val pad = PadState()
        pad.press(PadState.A, true)
        pad.press(PadState.START, true)
        assertTrue(pad.isDown(PadState.A))
        assertTrue(pad.isDown(PadState.START))
        assertFalse(pad.isDown(PadState.B))
        pad.press(PadState.A, false)
        assertFalse(pad.isDown(PadState.A))
        assertTrue(pad.isDown(PadState.START))
    }

    @Test fun hatReadsOpposingDirectionsAsNeutral() {
        val pad = PadState()
        pad.up = true
        assertEquals(-1, pad.hatY())
        pad.down = true
        assertEquals(0, pad.hatY())
        pad.up = false
        assertEquals(1, pad.hatY())
        pad.right = true
        assertEquals(1, pad.hatX())
        pad.left = true
        assertEquals(0, pad.hatX())
    }

    @Test fun clearResetsEverything() {
        val pad = PadState()
        pad.leftX = 0.5f
        pad.leftTrigger = 1f
        pad.up = true
        pad.press(PadState.A, true)
        pad.clear()
        assertEquals(0f, pad.leftX)
        assertEquals(0f, pad.leftTrigger)
        assertFalse(pad.up)
        assertFalse(pad.isDown(PadState.A))
    }

    @Test fun copyFromDuplicatesTheWholeState() {
        val a = PadState()
        a.rightX = -0.25f
        a.rightTrigger = 0.75f
        a.down = true
        a.press(PadState.GUIDE, true)
        val b = PadState()
        b.copyFrom(a)
        assertEquals(-0.25f, b.rightX)
        assertEquals(0.75f, b.rightTrigger)
        assertTrue(b.down)
        assertTrue(b.isDown(PadState.GUIDE))
    }
}
