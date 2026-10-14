package com.droiddeck.launcher.frontend

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GameEulaTest {
    private fun tempDir(): File = Files.createTempDirectory("eula").toFile()

    @Test fun freshGameRequiresEula() {
        assertFalse(GameEula.isAccepted(tempDir(), "620"))
    }

    @Test fun acceptOnceThenNeverAgain() {
        val dir = tempDir()
        GameEula.accept(dir, "620", atMs = 1234567890L)
        assertTrue(GameEula.isAccepted(dir, "620"))
        val acceptance = GameEula.acceptance(dir, "620")!!
        assertEquals("620", acceptance.appId)
        assertEquals(1234567890L, acceptance.acceptedAtMs)
    }

    @Test fun acceptanceIsPerGame() {
        val dir = tempDir()
        GameEula.accept(dir, "620")
        assertFalse(GameEula.isAccepted(dir, "621"))
    }

    @Test fun acceptanceSurvivesNewInstances() {
        val dir = tempDir()
        GameEula.accept(dir, "620", atMs = 42L)
        // A new "process" reading the same dir sees the acceptance.
        assertEquals(42L, GameEula.acceptance(dir, "620")!!.acceptedAtMs)
    }

    @Test fun revokeClearsAcceptance() {
        val dir = tempDir()
        GameEula.accept(dir, "620")
        assertTrue(GameEula.revoke(dir, "620"))
        assertFalse(GameEula.isAccepted(dir, "620"))
        assertFalse(GameEula.revoke(dir, "620"))
    }

    @Test fun corruptStoreIsTreatedAsNotAccepted() {
        val dir = tempDir()
        File(dir, GameEula.FILE).writeText("{oops")
        assertFalse(GameEula.isAccepted(dir, "620"))
        // Accepting again must repair the store.
        GameEula.accept(dir, "620")
        assertTrue(GameEula.isAccepted(dir, "620"))
    }

    @Test fun invalidAppIdsAreRejected() {
        val dir = tempDir()
        assertFalse(GameEula.isAccepted(dir, ""))
        assertFalse(GameEula.isAccepted(dir, "../x"))
        assertFalse(GameEula.isAccepted(dir, "0"))
        try {
            GameEula.accept(dir, "abc")
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
