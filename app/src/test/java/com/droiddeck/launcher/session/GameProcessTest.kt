package com.droiddeck.launcher.session

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GameProcessTest {
    private class FakeHandle(override val label: String) : GameProcessHandle {
        var alive = true
        var destroyed = false
        var destroyedForcibly = false
        var code: Int? = null
        override fun isAlive(): Boolean = alive
        override fun destroy() { destroyed = true }
        override fun destroyForcibly() { destroyedForcibly = true; alive = false; if (code == null) code = 143 }
        override fun exitCode(): Int? = code
        fun finish(code: Int) { alive = false; this.code = code }
    }

    private class FakeSpawner : GameProcessSpawner {
        val handles = mutableListOf<FakeHandle>()
        val spawns = mutableListOf<Triple<List<String>, Map<String, String>, File?>>()
        override fun spawn(command: List<String>, env: Map<String, String>, workDir: File?): GameProcessHandle {
            spawns.add(Triple(command, env, workDir))
            return FakeHandle(command.firstOrNull().orEmpty()).also { handles.add(it) }
        }
    }

    @Test fun startRegistersARunningGame() {
        val spawner = FakeSpawner()
        val mgr = GameProcessManager(spawner)
        mgr.start("620", listOf("wine", "game.exe"), mapOf("A" to "1"), null)
        assertTrue(mgr.isRunning("620"))
        assertEquals(1, mgr.liveCount())
        assertEquals(mapOf("A" to "1"), spawner.spawns.single().second)
    }

    @Test fun cleanExitUnregistersTheGame() {
        val spawner = FakeSpawner()
        val mgr = GameProcessManager(spawner)
        mgr.start("620", listOf("game"), emptyMap(), null)
        spawner.handles.single().finish(0)
        assertEquals(0, mgr.reap())
        assertFalse(mgr.isRunning("620"))
        assertEquals(0, mgr.liveCount())
    }

    @Test fun killOnAnAlreadyDeadProcessJustReportsTheCode() {
        val spawner = FakeSpawner()
        val mgr = GameProcessManager(spawner)
        mgr.start("620", listOf("game"), emptyMap(), null)
        val handle = spawner.handles.single()
        handle.finish(0) // exits on its own right as we ask
        assertEquals(0, mgr.kill("620"))
        assertFalse(handle.destroyed)
        assertFalse(handle.destroyedForcibly)
        assertFalse(mgr.isRunning("620"))
    }

    @Test fun killEscalatesToForcibleOnStubbornProcesses() {
        val spawner = FakeSpawner()
        val mgr = GameProcessManager(spawner)
        mgr.start("620", listOf("game"), emptyMap(), null)
        val handle = spawner.handles.single()
        // Never exits on its own: destroy() alone does not stop it.
        assertEquals(143, mgr.kill("620", graceMs = 50))
        assertTrue(handle.destroyed)
        assertTrue(handle.destroyedForcibly)
        assertEquals(0, mgr.liveCount())
    }

    @Test fun startingTwiceKillsTheOldHandleFirst() {
        val spawner = FakeSpawner()
        val mgr = GameProcessManager(spawner)
        mgr.start("620", listOf("game"), emptyMap(), null)
        val first = spawner.handles.single()
        mgr.start("620", listOf("game"), emptyMap(), null)
        assertTrue(first.destroyedForcibly)
        assertEquals(1, mgr.liveCount())
    }

    @Test fun killAllLeavesNoLiveHandles() {
        val spawner = FakeSpawner()
        val mgr = GameProcessManager(spawner)
        mgr.start("620", listOf("a"), emptyMap(), null)
        mgr.start("621", listOf("b"), emptyMap(), null)
        mgr.start("622", listOf("c"), emptyMap(), null)
        assertEquals(3, mgr.killAll())
        assertEquals(0, mgr.liveCount())
        assertTrue(spawner.handles.all { !it.isAlive() })
    }

    @Test fun killingAnUnknownAppIsAHarmlessNoOp() {
        val mgr = GameProcessManager(FakeSpawner())
        assertNull(mgr.kill("nope"))
        assertEquals(0, mgr.killAll())
    }

    @Test fun exitCodeOfSurvivesReap() {
        val spawner = FakeSpawner()
        val mgr = GameProcessManager(spawner)
        mgr.start("620", listOf("game"), emptyMap(), null)
        assertNull(mgr.exitCodeOf("620"))
        spawner.handles.single().finish(3)
        assertEquals(3, mgr.exitCodeOf("620"))
        mgr.reap()
        assertEquals(3, mgr.exitCodeOf("620"))
        assertNull(mgr.exitCodeOf("nope"))
    }
}
