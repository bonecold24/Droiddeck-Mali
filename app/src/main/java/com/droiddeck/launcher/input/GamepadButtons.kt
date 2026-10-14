package com.droiddeck.launcher.input

import android.view.KeyEvent

/**
 * Item 6: the gamepad button mapping table (Android keycode -> pad action).
 *
 * This is the pure, unit-testable twin of the switch in
 * PadBridge.onKeyEvent, which delegates to [actionFor]. The indices are
 * PadState's button slots (the X360 layout the guest's fake input devices
 * expose); L2/R2 are analog triggers and the dpad is four directions.
 */
object GamepadButtons {
    sealed interface Action {
        data class Button(val index: Int) : Action
        data class Trigger(val left: Boolean) : Action
        data class Dpad(val direction: Direction) : Action
    }

    enum class Direction { UP, DOWN, LEFT, RIGHT }

    /**
     * Maps an Android keycode to a pad action, or null when the key is not a
     * gamepad control. [selectAsGuide] mirrors PadBridge's selectSteam switch:
     * SELECT opens the Steam overlay instead of pressing index 6.
     */
    @JvmStatic
    fun actionFor(keyCode: Int, selectAsGuide: Boolean): Action? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> Action.Button(PadState.A)
        KeyEvent.KEYCODE_BUTTON_B -> Action.Button(PadState.B)
        KeyEvent.KEYCODE_BUTTON_X -> Action.Button(PadState.X)
        KeyEvent.KEYCODE_BUTTON_Y -> Action.Button(PadState.Y)
        KeyEvent.KEYCODE_BUTTON_L1 -> Action.Button(PadState.LB)
        KeyEvent.KEYCODE_BUTTON_R1 -> Action.Button(PadState.RB)
        KeyEvent.KEYCODE_BUTTON_SELECT ->
            Action.Button(if (selectAsGuide) PadState.GUIDE else PadState.SELECT)
        KeyEvent.KEYCODE_BACK -> Action.Button(PadState.SELECT)
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> Action.Button(PadState.START)
        KeyEvent.KEYCODE_BUTTON_THUMBL -> Action.Button(PadState.L3)
        KeyEvent.KEYCODE_BUTTON_THUMBR -> Action.Button(PadState.R3)
        KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_HOME -> Action.Button(PadState.GUIDE)
        KeyEvent.KEYCODE_BUTTON_L2 -> Action.Trigger(left = true)
        KeyEvent.KEYCODE_BUTTON_R2 -> Action.Trigger(left = false)
        KeyEvent.KEYCODE_DPAD_UP -> Action.Dpad(Direction.UP)
        KeyEvent.KEYCODE_DPAD_DOWN -> Action.Dpad(Direction.DOWN)
        KeyEvent.KEYCODE_DPAD_LEFT -> Action.Dpad(Direction.LEFT)
        KeyEvent.KEYCODE_DPAD_RIGHT -> Action.Dpad(Direction.RIGHT)
        else -> null
    }
}
