package com.droiddeck.launcher.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Item 6: touch->input for the keyboard path. EvdevKeys translates Android
 * keycodes (physical keyboards and the on-screen PC keyboard) to Linux evdev
 * codes that the guest's fake input devices emit.
 */
class EvdevKeysTest {

    @Test fun lettersMapToEvdevCodes() {
        assertEquals(30, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_A))
        assertEquals(48, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_B))
        assertEquals(16, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_Q))
        assertEquals(50, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_M))
        assertEquals(44, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_Z))
    }

    @Test fun digitsMapToEvdevCodes() {
        assertEquals(2, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_1))
        assertEquals(10, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_9))
        assertEquals(11, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_0))
    }

    @Test fun controlKeysMapToEvdevCodes() {
        assertEquals(28, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_ENTER))
        assertEquals(28, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertEquals(57, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_SPACE))
        assertEquals(15, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_TAB))
        assertEquals(1, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(14, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_DEL))
        assertEquals(111, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_FORWARD_DEL))
    }

    @Test fun modifiersMapToEvdevCodes() {
        assertEquals(42, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT))
        assertEquals(54, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_SHIFT_RIGHT))
        assertEquals(29, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_CTRL_LEFT))
        assertEquals(97, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_CTRL_RIGHT))
        assertEquals(56, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_ALT_LEFT))
        assertEquals(100, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_ALT_RIGHT))
    }

    @Test fun functionKeysMapToEvdevCodes() {
        assertEquals(59, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_F1))
        assertEquals(68, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_F10))
        assertEquals(87, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_F11))
        assertEquals(88, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_F12))
    }

    @Test fun arrowsAndNavigationMapToEvdevCodes() {
        assertEquals(103, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(108, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(105, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(106, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(102, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_MOVE_HOME))
        assertEquals(107, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_MOVE_END))
    }

    @Test fun unmappedKeysReturnMinusOne() {
        assertEquals(-1, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_UNKNOWN))
        assertEquals(-1, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(-1, EvdevKeys.fromKeyCode(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test fun unshiftedCharStripsShift() {
        assertEquals('a'.code, EvdevKeys.unshiftedChar('A'.code))
        assertEquals('1'.code, EvdevKeys.unshiftedChar('!'.code))
        assertEquals('/'.code, EvdevKeys.unshiftedChar('?'.code))
        assertEquals(0, EvdevKeys.unshiftedChar(' '.code))
    }

    @Test fun keycodeForCharCoversLettersDigitsAndPunctuation() {
        assertEquals(KeyEvent.KEYCODE_A, EvdevKeys.keycodeForChar('a'.code))
        assertEquals(KeyEvent.KEYCODE_Z, EvdevKeys.keycodeForChar('z'.code))
        assertEquals(KeyEvent.KEYCODE_0, EvdevKeys.keycodeForChar('0'.code))
        assertEquals(KeyEvent.KEYCODE_MINUS, EvdevKeys.keycodeForChar('-'.code))
        assertEquals(KeyEvent.KEYCODE_SLASH, EvdevKeys.keycodeForChar('/'.code))
        assertEquals(KeyEvent.KEYCODE_SPACE, EvdevKeys.keycodeForChar(' '.code))
        assertEquals(0, EvdevKeys.keycodeForChar('@'.code))
    }
}
