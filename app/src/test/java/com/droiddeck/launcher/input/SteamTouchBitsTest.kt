package com.droiddeck.launcher.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 6: the touch→input event mapping.
 *
 * Pins the per-element mapping SteamTouchControls.publish() folds into the
 * 64-bit button mask and the stick/trackpad axes: the button-bit table, the
 * d-pad angle sectors, the stick normalization and the trackpad absolute
 * encoding. Rendering the controls is NEEDS-REAL-MALI; the bit math is not.
 */
class SteamTouchBitsTest {

    @Test fun faceButtonsMapToTheirBits() {
        assertEquals(1L shl 7, SteamTouchBits.buttonBit(SteamTouchConfig.A))
        assertEquals(1L shl 5, SteamTouchBits.buttonBit(SteamTouchConfig.B))
        assertEquals(1L shl 6, SteamTouchBits.buttonBit(SteamTouchConfig.X))
        assertEquals(1L shl 4, SteamTouchBits.buttonBit(SteamTouchConfig.Y))
    }

    @Test fun systemAndShoulderButtonsMapToTheirBits() {
        assertEquals(1L shl 13, SteamTouchBits.buttonBit(SteamTouchConfig.STEAM))
        assertEquals(1L shl 12, SteamTouchBits.buttonBit(SteamTouchConfig.SELECT))
        assertEquals(1L shl 14, SteamTouchBits.buttonBit(SteamTouchConfig.START))
        assertEquals(1L shl 3, SteamTouchBits.buttonBit(SteamTouchConfig.BUMPER_LEFT))
        assertEquals(1L shl 2, SteamTouchBits.buttonBit(SteamTouchConfig.BUMPER_RIGHT))
        assertEquals(1L shl 1, SteamTouchBits.buttonBit(SteamTouchConfig.TRIGGER_LEFT))
        assertEquals(1L shl 0, SteamTouchBits.buttonBit(SteamTouchConfig.TRIGGER_RIGHT))
        assertEquals(1L shl 22, SteamTouchBits.buttonBit(SteamTouchConfig.JOYSTICK_LEFT_BUTTON))
        assertEquals(1L shl 26, SteamTouchBits.buttonBit(SteamTouchConfig.JOYSTICK_RIGHT_BUTTON))
    }

    @Test fun macrosMapToTheHighBits() {
        for (i in 0 until 8) {
            assertEquals(1L shl (32 + i), SteamTouchBits.buttonBit(SteamTouchConfig.MACRO_0 + i))
        }
        assertEquals(1L shl 48, SteamTouchBits.buttonBit(SteamTouchConfig.MACRO_1_FINGER))
        assertEquals(1L shl 49, SteamTouchBits.buttonBit(SteamTouchConfig.MACRO_2_FINGER))
    }

    @Test fun menuActionsAndAnalogElementsCarryNoButtonBit() {
        assertEquals(0L, SteamTouchBits.buttonBit(SteamTouchConfig.THUMB))
        assertEquals(0L, SteamTouchBits.buttonBit(SteamTouchConfig.KEYBOARD))
        assertEquals(0L, SteamTouchBits.buttonBit(SteamTouchConfig.PASTE))
        assertEquals(0L, SteamTouchBits.buttonBit(SteamTouchConfig.DPAD))
        assertEquals(0L, SteamTouchBits.buttonBit(SteamTouchConfig.JOYSTICK_LEFT))
        assertEquals(0L, SteamTouchBits.buttonBit(SteamTouchConfig.TRACKPAD_CENTER))
    }

    @Test fun dpadDeadZoneReportsNothing() {
        assertEquals(0L, SteamTouchBits.dpadBits(0f, 0f, 10f))
        assertEquals(0L, SteamTouchBits.dpadBits(9.9f, 0f, 10f))
    }

    @Test fun dpadCardinalDirections() {
        val dead = 1f
        assertEquals(SteamTouchBits.DPAD_RIGHT, SteamTouchBits.dpadBits(50f, 0f, dead))
        assertEquals(SteamTouchBits.DPAD_LEFT, SteamTouchBits.dpadBits(-50f, 0f, dead))
        // Screen Y is down, so up is -dy.
        assertEquals(SteamTouchBits.DPAD_UP, SteamTouchBits.dpadBits(0f, -50f, dead))
        assertEquals(SteamTouchBits.DPAD_DOWN, SteamTouchBits.dpadBits(0f, 50f, dead))
    }

    @Test fun dpadDiagonalsSetBothDirections() {
        val dead = 1f
        assertEquals(
            SteamTouchBits.DPAD_RIGHT or SteamTouchBits.DPAD_UP,
            SteamTouchBits.dpadBits(50f, -50f, dead),
        )
        assertEquals(
            SteamTouchBits.DPAD_LEFT or SteamTouchBits.DPAD_DOWN,
            SteamTouchBits.dpadBits(-50f, 50f, dead),
        )
    }

    @Test fun stickCenterIsZeroAndFullDeflectionIsFullScale() {
        val (x0, y0) = SteamTouchBits.stickAxes(0f, 0f, 90f, 90f)
        assertEquals(0.toShort(), x0)
        assertEquals(0.toShort(), y0)

        val (xr, yr) = SteamTouchBits.stickAxes(90f, 0f, 90f, 90f)
        assertEquals(32767.toShort(), xr)
        assertEquals(0.toShort(), yr)

        // Screen Y is down: pushing up is +32767 on the axis.
        val (xu, yu) = SteamTouchBits.stickAxes(0f, -90f, 90f, 90f)
        assertEquals(0.toShort(), xu)
        assertEquals(32767.toShort(), yu)
    }

    @Test fun stickDeflectionIsClampedToTheUnitCircle() {
        val (x, y) = SteamTouchBits.stickAxes(180f, 0f, 90f, 90f)
        assertEquals(32767.toShort(), x)
        assertEquals(0.toShort(), y)

        val (xd, yd) = SteamTouchBits.stickAxes(90f, 90f, 90f, 90f)
        // 1/sqrt(2) * 32767 = 23169.77, truncated toward zero
        assertEquals(23169.toShort(), xd)
        assertEquals((-23169).toShort(), yd)
    }

    @Test fun trackpadCornersEncodeAsUnsignedPairs() {
        val (x0, y0) = SteamTouchBits.trackpadPair(0f, 0f)
        assertEquals((-32768).toShort(), x0)
        assertEquals(32767.toShort(), y0)

        val (x1, y1) = SteamTouchBits.trackpadPair(1f, 1f)
        assertEquals(32767.toShort(), x1)
        assertEquals((-32768).toShort(), y1)
    }

    @Test fun trackpadCenterEncodesNearTheMiddle() {
        val (x, y) = SteamTouchBits.trackpadPair(0.5f, 0.5f)
        // 0.5 * 65535 truncates to 32767: x = 32767 ^ 0x8000, y = 32767 ^ 0x7fff.
        assertEquals((-1).toShort(), x)
        assertEquals(0.toShort(), y)
    }

    @Test fun hasButtonMarksTheSlideableControls() {
        assertTrue(SteamTouchBits.hasButton(SteamTouchConfig.A))
        assertTrue(SteamTouchBits.hasButton(SteamTouchConfig.START))
        assertTrue(SteamTouchBits.hasButton(SteamTouchConfig.MACRO_0 + 3))
        // Menu actions carry a zero bit but still count for slide handling.
        assertTrue(SteamTouchBits.hasButton(SteamTouchConfig.THUMB))
        assertTrue(SteamTouchBits.hasButton(SteamTouchConfig.KEYBOARD))
        assertFalse(SteamTouchBits.hasButton(SteamTouchConfig.DPAD))
        assertFalse(SteamTouchBits.hasButton(SteamTouchConfig.JOYSTICK_LEFT))
        assertFalse(SteamTouchBits.hasButton(SteamTouchConfig.TRACKPAD_LEFT))
    }
}
