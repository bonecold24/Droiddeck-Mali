package com.droiddeck.launcher.input

/**
 * Item 6: the 40-byte touch report shared with the guest, as pure,
 * host-testable functions.
 *
 * Layout (little-endian), mirrored by struct TouchRingFile in
 * app/src/main/cpp/fakeinput_steam.cpp (TOUCH_REPORT_BYTES = 40):
 * bytes 0-7 the button bitmask, 8-15 the four stick axes, 16-27 the six
 * trackpad shorts, 28-39 the motion block. encodeInto writes bytes 0-27 and
 * leaves 28-39 untouched, exactly like SteamTouchDevice.setControls did
 * before it delegated here.
 */
object SteamTouchReport {
    const val REPORT_BYTES = 40

    /** Button mask (8 bytes), stick axes (4 shorts), trackpad pairs (6 shorts). */
    fun encodeInto(into: ByteArray, buttons: Long, sticks: ShortArray, pads: ShortArray) {
        for (i in 0 until 8) into[i] = (buttons ushr (8 * i)).toByte()
        for (i in 0 until 4) putShort(into, 8 + 2 * i, sticks[i])
        for (i in 0 until 6) putShort(into, 16 + 2 * i, pads[i])
    }

    fun putShort(into: ByteArray, at: Int, value: Short) {
        into[at] = value.toInt().toByte()
        into[at + 1] = (value.toInt() shr 8).toByte()
    }
}
