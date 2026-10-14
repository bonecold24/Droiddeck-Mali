package com.droiddeck.launcher.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 6: the gamepad button mapping table (Android keycode -> pad action),
 * extracted from PadBridge.onKeyEvent so it is unit-testable.
 */
class GamepadButtonsTest {

    @Test fun faceButtonsMapToTheFirstFourIndices() {
        assertEquals(GamepadButtons.Action.Button(PadState.A), GamepadButtons.actionFor(KeyEvent.KEYCODE_BUTTON_A, false))
        assertEquals(GamepadButtons.Action.Button(PadState.B), GamepadButtons.actionFor(KeyEvent.KEYCODE_BUTTON_B, false))
        assertEquals(GamepadButtons.Action.Button(PadState.X), GamepadButtons.actionFor(KeyEvent.KEYCODE_BUTTON_X, false))
        assertEquals(GamepadButtons.Action.Button(PadState.Y), GamepadButtons.actionFor(KeyEvent.KEYCODE_BUTTON_Y, false))
        assertEquals(0, PadState.A)
        assertEquals(3, PadState.Y)
    }

    @Test fun shouldersMapToIndicesFourAndFive() {
        assertEquals(GamepadButtons.Action.Button(4), GamepadButtons.actionFor(KeyEvent.KEYCODE_BUTTON_L1, false))
        assertEquals(GamepadButtons.Action.Button(5), GamepadButtons.actionFor(KeyEvent.KEYCODE_BUTTON_R1, false))
    }

    @Test fun selectStaysSelectUnlessItIsBoundToSteam() {
        assertEquals(
            GamepadButtons.Action.Button(PadState.SELECT),
            GamepadButtons.actionFor(KeyEvent.KEYCODE_BUTTON_SELECT, selectAsGuide = false),
        )
        assertEquals(
            GamepadButtons.Action.Button(PadState.GUIDE),
            GamepadButtons.actionFor(KeyEvent.KEYCODE_BUTTON_SELECT, selectAsGuide = true),
        )
        assertEquals(
            GamepadButtons.Action.Button(PadState.SELECT),
            GamepadButtons.actionFor(KeyEvent.KEYCODE_BACK, selectAsGuide = true),
        )
    }

    @Test fun startAndMenuShareIndexSeven() {
        assertEquals(GamepadButtons.Action.Button(7), GamepadButtons.actionFor(KeyEvent.KEYCODE_BUTTON_START, false))
        assertEquals(GamepadButtons.Action.Button(7), GamepadButtons.actionFor(KeyEvent.KEYCODE_MENU, false))
    }

    @Test fun stickClicksMapToEightAndNine() {
        assertEquals(GamepadButtons.Action.Button(8), GamepadButtons.actionFor(KeyEvent.KEYCODE_BUTTON_THUMBL, false))
        assertEquals(GamepadButtons.Action.Button(9), GamepadButtons.actionFor(KeyEvent.KEYCODE_BUTTON_THUMBR, false))
    }

    @Test fun modeAndHomeOpenTheGuideOverlay() {
        assertEquals(
            GamepadButtons.Action.Button(PadState.GUIDE),
            GamepadButtons.actionFor(KeyEvent.KEYCODE_BUTTON_MODE, false),
        )
        assertEquals(
            GamepadButtons.Action.Button(PadState.GUIDE),
            GamepadButtons.actionFor(KeyEvent.KEYCODE_HOME, false),
        )
        assertEquals(12, PadState.GUIDE)
    }

    @Test fun analogTriggersAreTriggersNotButtons() {
        assertEquals(GamepadButtons.Action.Trigger(left = true), GamepadButtons.actionFor(KeyEvent.KEYCODE_BUTTON_L2, false))
        assertEquals(GamepadButtons.Action.Trigger(left = false), GamepadButtons.actionFor(KeyEvent.KEYCODE_BUTTON_R2, false))
    }

    @Test fun dpadMapsToDirections() {
        assertEquals(
            GamepadButtons.Action.Dpad(GamepadButtons.Direction.UP),
            GamepadButtons.actionFor(KeyEvent.KEYCODE_DPAD_UP, false),
        )
        assertEquals(
            GamepadButtons.Action.Dpad(GamepadButtons.Direction.DOWN),
            GamepadButtons.actionFor(KeyEvent.KEYCODE_DPAD_DOWN, false),
        )
        assertEquals(
            GamepadButtons.Action.Dpad(GamepadButtons.Direction.LEFT),
            GamepadButtons.actionFor(KeyEvent.KEYCODE_DPAD_LEFT, false),
        )
        assertEquals(
            GamepadButtons.Action.Dpad(GamepadButtons.Direction.RIGHT),
            GamepadButtons.actionFor(KeyEvent.KEYCODE_DPAD_RIGHT, false),
        )
    }

    @Test fun unknownKeysMapToNothing() {
        assertNull(GamepadButtons.actionFor(KeyEvent.KEYCODE_A, false))
        assertNull(GamepadButtons.actionFor(KeyEvent.KEYCODE_ENTER, false))
        assertNull(GamepadButtons.actionFor(KeyEvent.KEYCODE_UNKNOWN, false))
    }

    @Test fun theTableCoversEveryButtonPadBridgeRouted() {
        val routed = listOf(
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        )
        for (key in routed) assertTrue("key $key unmapped", GamepadButtons.actionFor(key, false) != null)
    }
}
