package com.droiddeck.launcher.input

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Item 6: the touch→input event mapping of SteamTouchControls as pure,
 * host-testable functions.
 *
 * SteamTouchControls.publish() folds every active finger into one 64-bit
 * button mask plus stick/trackpad axes; this object holds the per-element
 * mapping: the button-bit table, the d-pad angle sectors, the stick
 * normalization and the trackpad absolute encoding. The view keeps the
 * Android touch handling and delegates the bit math here, so the table has
 * a single source of truth.
 *
 * The bits feed the 40-byte report in SteamTouchReport; the guest side
 * (app/src/main/cpp/fakeinput_steam.cpp, TOUCH_RING_MAGIC 0x31484354) reads
 * the same layout.
 */
object SteamTouchBits {
    // D-pad direction bits.
    const val DPAD_UP = 1L shl 8
    const val DPAD_RIGHT = 1L shl 9
    const val DPAD_LEFT = 1L shl 10
    const val DPAD_DOWN = 1L shl 11

    // Stick contact bits.
    const val STICK_LEFT_TOUCHED = 1L shl 46
    const val STICK_RIGHT_TOUCHED = 1L shl 47

    // Trackpad contact bits, indexed by (type - TRACKPAD_CENTER): center, left, right.
    val TRACKPAD_TOUCHED = longArrayOf(1L shl 27, 1L shl 19, 1L shl 20)

    /**
     * The button bit for a control type, or 0 when the type carries no
     * gamepad bit: THUMB/KEYBOARD/PASTE are menu actions, and the d-pad,
     * sticks and trackpads are encoded by dpadBits/stickAxes/trackpadPair.
     */
    fun buttonBit(type: Int): Long = when (type) {
        SteamTouchConfig.STEAM -> 1L shl 13
        SteamTouchConfig.JOYSTICK_LEFT_BUTTON -> 1L shl 22
        SteamTouchConfig.JOYSTICK_RIGHT_BUTTON -> 1L shl 26
        SteamTouchConfig.A -> 1L shl 7
        SteamTouchConfig.B -> 1L shl 5
        SteamTouchConfig.X -> 1L shl 6
        SteamTouchConfig.Y -> 1L shl 4
        SteamTouchConfig.SELECT -> 1L shl 12
        SteamTouchConfig.START -> 1L shl 14
        SteamTouchConfig.TRIGGER_LEFT -> 1L shl 1
        SteamTouchConfig.TRIGGER_RIGHT -> 1L shl 0
        SteamTouchConfig.BUMPER_LEFT -> 1L shl 3
        SteamTouchConfig.BUMPER_RIGHT -> 1L shl 2
        in SteamTouchConfig.MACRO_0..SteamTouchConfig.MACRO_0 + 7 -> 1L shl (32 + (type - SteamTouchConfig.MACRO_0))
        SteamTouchConfig.MACRO_1_FINGER -> 1L shl 48
        SteamTouchConfig.MACRO_2_FINGER -> 1L shl 49
        else -> 0L
    }

    /**
     * True for control types that participate in button press/slide handling
     * (the keys of the old BUTTON_BITS table): the gamepad buttons plus the
     * menu actions, which carry a zero bit. The d-pad, sticks and trackpads
     * are encoded separately and return false.
     */
    fun hasButton(type: Int): Boolean = when (type) {
        SteamTouchConfig.STEAM,
        SteamTouchConfig.JOYSTICK_LEFT_BUTTON, SteamTouchConfig.JOYSTICK_RIGHT_BUTTON,
        SteamTouchConfig.A, SteamTouchConfig.B, SteamTouchConfig.X, SteamTouchConfig.Y,
        SteamTouchConfig.SELECT, SteamTouchConfig.START,
        SteamTouchConfig.TRIGGER_LEFT, SteamTouchConfig.TRIGGER_RIGHT,
        SteamTouchConfig.BUMPER_LEFT, SteamTouchConfig.BUMPER_RIGHT,
        SteamTouchConfig.MACRO_1_FINGER, SteamTouchConfig.MACRO_2_FINGER,
        SteamTouchConfig.THUMB, SteamTouchConfig.KEYBOARD, SteamTouchConfig.PASTE -> true
        in SteamTouchConfig.MACRO_0..SteamTouchConfig.MACRO_0 + 7 -> true
        else -> false
    }

    /**
     * D-pad direction bits for one finger at (dx, dy) relative to the pad
     * center. Inside the dead radius (0.22 * the pad radius in the view) no
     * direction is reported.
     */
    fun dpadBits(dx: Float, dy: Float, deadRadius: Float): Long {
        if (hypot(dx, dy) < deadRadius) return 0L
        val angle = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())).let { if (it < 0) it + 360 else it }
        var bits = 0L
        if (angle < 67.5 || angle > 292.5) bits = bits or DPAD_RIGHT
        if (angle in 22.5..157.5) bits = bits or DPAD_UP
        if (angle in 112.5..247.5) bits = bits or DPAD_LEFT
        if (angle in 202.5..337.5) bits = bits or DPAD_DOWN
        return bits
    }

    /**
     * Stick axes for a finger offset (dx, dy) from the stick center with
     * radii (r, ry): normalized to [-1, 1], clamped to the unit circle, Y
     * flipped (screen Y points down), scaled to signed 16-bit.
     */
    fun stickAxes(dx: Float, dy: Float, r: Float, ry: Float): Pair<Short, Short> {
        var nx = dx / r
        var ny = dy / ry
        val d = hypot(nx, ny)
        if (d > 1f) {
            nx /= d
            ny /= d
        }
        return (nx * 32767).toInt().coerceIn(-32767, 32767).toShort() to
            (-ny * 32767).toInt().coerceIn(-32767, 32767).toShort()
    }

    /**
     * Trackpad absolute position: (nx, ny) in [0, 1] become the two shorts
     * the guest reads as an unsigned 16-bit pair (x ^ 0x8000, y ^ 0x7fff).
     */
    fun trackpadPair(nx: Float, ny: Float): Pair<Short, Short> {
        val x = (nx * 65535).toInt().coerceIn(0, 65535)
        val y = (ny * 65535).toInt().coerceIn(0, 65535)
        return (x xor 0x8000).toShort() to (y xor 0x7fff).toShort()
    }
}
