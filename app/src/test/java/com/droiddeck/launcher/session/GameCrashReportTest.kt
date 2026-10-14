package com.droiddeck.launcher.session

import com.droiddeck.launcher.core.MiniJson
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GameCrashReportTest {
    private fun report(exit: ProcessExit, tail: List<String> = listOf("l1", "l2")) =
        GameCrashReport.build(
            appId = "620",
            sessionId = "s1",
            startedAtMs = 1000L,
            endedAtMs = 2000L,
            exit = exit,
            tail = tail,
        )

    @Test fun abnormalExitIsNotClean() {
        val r = report(ProcessExit.Code(1))
        assertFalse(r.clean)
        assertTrue(r.toText().contains("620"))
        assertTrue(r.toText().contains("exit code 1"))
        assertTrue(r.toText().contains("l2"))
    }

    @Test fun signalDeathIsReported() {
        val r = report(ProcessExit.Signal("SIGKILL"))
        assertFalse(r.clean)
        assertTrue(r.toText().contains("SIGKILL"))
    }

    @Test fun cleanExitIsMarkedClean() {
        assertTrue(report(ProcessExit.Code(0)).clean)
    }

    @Test fun unknownExitIsNotClean() {
        assertFalse(report(ProcessExit.Unknown).clean)
    }

    @Test fun jsonRoundTrips() {
        val r = report(ProcessExit.Code(139), listOf("segfault here"))
        @Suppress("UNCHECKED_CAST")
        val m = MiniJson.parse(r.toJson()) as Map<String, Any?>
        assertEquals("620", m["appId"])
        assertEquals("s1", m["sessionId"])
        assertEquals(139L, (m["exit"] as Map<*, *>)["code"])
        assertEquals(listOf("segfault here"), m["logTail"])
        assertEquals(false, m["clean"])
    }

    @Test fun logTailIsCapped() {
        val r = report(ProcessExit.Code(1), (0 until 1000).map { "line$it" })
        assertTrue(r.logTail.size <= GameCrashReport.MAX_TAIL_LINES)
        assertEquals("line999", r.logTail.last())
    }

    @Test fun writePersistsJsonAndText() {
        val dir = Files.createTempDirectory("crash").toFile()
        val file = GameCrashReportStore.write(dir, report(ProcessExit.Code(1)))
        assertTrue(file.isFile)
        assertTrue(File(dir, "crash-s1.txt").isFile)
        assertTrue(file.readText().contains("\"appId\":\"620\""))
    }

    @Test fun writeRejectsBadSessionIds() {
        val dir = Files.createTempDirectory("crash").toFile()
        try {
            GameCrashReportStore.write(dir, report(ProcessExit.Code(1)).copy(sessionId = "../x"))
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
