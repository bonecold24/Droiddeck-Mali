package com.droiddeck.launcher.frontend

import org.junit.Assert.*
import org.junit.Test

class SteamUriParseTest {
    private fun parse(scheme: String?, host: String?, vararg segments: String) =
        GameLaunchIntent.parseSteamUriParts(scheme, host, segments.toList())

    @Test fun rungameidResolves() {
        assertEquals("620", parse("steam", "rungameid", "620"))
        assertEquals("570", parse("steam", "run", "570"))
    }

    @Test fun schemeAndHostAreCaseInsensitive() {
        assertEquals("620", parse("STEAM", "RUNGAMEID", "620"))
        assertEquals("620", parse("Steam", "RunGameId", "620"))
    }

    @Test fun unsignedShortcutIdsAreAccepted() {
        assertEquals("18446744073709551615", parse("steam", "rungameid", "18446744073709551615"))
    }

    @Test fun nonSteamSchemesAreIgnored() {
        assertNull(parse("http", "rungameid", "620"))
        assertNull(parse("droiddeck", "game", "620"))
        assertNull(parse(null, "rungameid", "620"))
    }

    @Test fun nonGameHostsAreIgnored() {
        assertNull(parse("steam", "store", "620"))
        assertNull(parse("steam", "openurl", "620"))
        assertNull(parse("steam", null, "620"))
    }

    @Test fun missingOrInvalidIdsAreRejected() {
        assertNull(parse("steam", "rungameid"))
        assertNull(parse("steam", "rungameid", ""))
        assertNull(parse("steam", "rungameid", "0"))
        assertNull(parse("steam", "rungameid", "abc"))
        assertNull(parse("steam", "rungameid", "620\n"))
        assertNull(parse("steam", "rungameid", "-1"))
    }

    @Test fun firstSegmentWins() {
        assertEquals("620", parse("steam", "rungameid", "620", "extra"))
    }
}
