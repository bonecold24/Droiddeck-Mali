package com.droiddeck.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Hardening tests for the child-process (phantom-process) limit handling:
 * verification-failure classification and saved wireless-ADB pairing parsing.
 */
class RestrictionHardeningTest {
    // PhantomProcessLimit.verifyFailureHint()

    @Test fun verifyHintOkWhenValueMatches() {
        assertEquals(
            PhantomProcessLimit.VerifyHint.OK,
            PhantomProcessLimit.verifyFailureHint("false", enabled = false, sdk = 33),
        )
        assertEquals(
            PhantomProcessLimit.VerifyHint.OK,
            PhantomProcessLimit.verifyFailureHint("2147483647\n", enabled = false, sdk = 31),
        )
        assertEquals(
            PhantomProcessLimit.VerifyHint.OK,
            PhantomProcessLimit.verifyFailureHint("true", enabled = true, sdk = 34),
        )
    }

    @Test fun verifyHintEmptyOutput() {
        assertEquals(
            PhantomProcessLimit.VerifyHint.EMPTY_OUTPUT,
            PhantomProcessLimit.verifyFailureHint("  \r\n ", enabled = false, sdk = 33),
        )
    }

    @Test fun verifyHintNotSetValue() {
        assertEquals(
            PhantomProcessLimit.VerifyHint.NOT_SET_VALUE,
            PhantomProcessLimit.verifyFailureHint("null", enabled = false, sdk = 33),
        )
        assertEquals(
            PhantomProcessLimit.VerifyHint.NOT_SET_VALUE,
            PhantomProcessLimit.verifyFailureHint("null\n", enabled = false, sdk = 31),
        )
    }

    @Test fun verifyHintWrongValue() {
        assertEquals(
            PhantomProcessLimit.VerifyHint.WRONG_VALUE,
            PhantomProcessLimit.verifyFailureHint("true", enabled = false, sdk = 33),
        )
        assertEquals(
            PhantomProcessLimit.VerifyHint.WRONG_VALUE,
            PhantomProcessLimit.verifyFailureHint("0", enabled = false, sdk = 31),
        )
    }

    // WirelessAdbFix.parseSavedConnection()

    @Test fun parsesValidSavedConnection() {
        val c = WirelessAdbFix.parseSavedConnection("192.168.1.5", listOf("192.168.1.5", "41234"))
        assertEquals("192.168.1.5", c?.host)
        assertEquals(41234, c?.port)
    }

    @Test fun missingConnectionFileFallsBackToDiscovery() {
        val c = WirelessAdbFix.parseSavedConnection("192.168.1.5", null)
        assertEquals("192.168.1.5", c?.host)
        assertNull(c?.port)
    }

    @Test fun mismatchedHostFallsBackToDiscovery() {
        val c = WirelessAdbFix.parseSavedConnection("192.168.1.5", listOf("10.0.0.2", "41234"))
        assertEquals("192.168.1.5", c?.host)
        assertNull(c?.port)
    }

    @Test fun badPortFallsBackToDiscovery() {
        assertNull(WirelessAdbFix.parseSavedConnection("h", listOf("h", "99999"))?.port)
        assertNull(WirelessAdbFix.parseSavedConnection("h", listOf("h", "0"))?.port)
        assertNull(WirelessAdbFix.parseSavedConnection("h", listOf("h", "abc"))?.port)
        assertNull(WirelessAdbFix.parseSavedConnection("h", listOf("h", "1", "2"))?.port)
        assertNull(WirelessAdbFix.parseSavedConnection("h", listOf("h"))?.port)
    }

    @Test fun hostWithNewlineIsRejected() {
        assertNull(WirelessAdbFix.parseSavedConnection("a\nb", listOf("a", "b", "1234")))
        assertNull(WirelessAdbFix.parseSavedConnection("a\rb", null))
        assertNull(WirelessAdbFix.parseSavedConnection("", null))
    }

    @Test fun ipv6HostParses() {
        val c = WirelessAdbFix.parseSavedConnection("::1", listOf("::1", "5555"))
        assertEquals("::1", c?.host)
        assertEquals(5555, c?.port)
    }
}
