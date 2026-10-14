package com.droiddeck.launcher.input

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Item 6: the 40-byte touch report shared with the guest.
 *
 * Pins the little-endian layout struct TouchRingFile in
 * app/src/main/cpp/fakeinput_steam.cpp reads: bytes 0-7 the button bitmask,
 * 8-15 the four stick axes, 16-27 the six trackpad shorts; bytes 28-39 are
 * the motion block and are left untouched here.
 */
class SteamTouchReportTest {

    @Test fun reportIs40Bytes() {
        assertEquals(40, SteamTouchReport.REPORT_BYTES)
    }

    @Test fun buttonsEncodeLittleEndianInTheFirstEightBytes() {
        val out = ByteArray(40)
        SteamTouchReport.encodeInto(out, 0x0102030405060708L, ShortArray(4), ShortArray(6))
        assertArrayEquals(
            byteArrayOf(0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01),
            out.copyOfRange(0, 8),
        )
    }

    @Test fun sticksFollowTheButtonsAsFourLittleEndianShorts() {
        val out = ByteArray(40)
        SteamTouchReport.encodeInto(out, 0L, shortArrayOf(1, -1, 32767, -32768), ShortArray(6))
        assertArrayEquals(
            byteArrayOf(0x01, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F, 0x00, 0x80.toByte()),
            out.copyOfRange(8, 16),
        )
    }

    @Test fun trackpadsFollowTheSticksAsSixLittleEndianShorts() {
        val out = ByteArray(40)
        SteamTouchReport.encodeInto(
            out, 0L, ShortArray(4),
            shortArrayOf(0x1234, -0x1234, 0, 1, -1, 0x7FFF),
        )
        assertArrayEquals(
            byteArrayOf(
                0x34, 0x12, 0xCC.toByte(), 0xED.toByte(),
                0x00, 0x00, 0x01, 0x00,
                0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F,
            ),
            out.copyOfRange(16, 28),
        )
    }

    @Test fun theMotionBlockIsLeftUntouched() {
        val out = ByteArray(40) { 0x5A }
        SteamTouchReport.encodeInto(out, -1L, ShortArray(4) { -1 }, ShortArray(6) { -1 })
        assertArrayEquals(ByteArray(12) { 0x5A }, out.copyOfRange(28, 40))
    }

    @Test fun aFullButtonMaskRoundTrips() {
        val buttons = SteamTouchBits.buttonBit(SteamTouchConfig.A) or
            SteamTouchBits.buttonBit(SteamTouchConfig.B) or
            SteamTouchBits.DPAD_UP
        val out = ByteArray(40)
        SteamTouchReport.encodeInto(out, buttons, ShortArray(4), ShortArray(6))
        var back = 0L
        for (i in 0 until 8) back = back or ((out[i].toLong() and 0xFF) shl (8 * i))
        assertEquals(buttons, back)
    }
}
