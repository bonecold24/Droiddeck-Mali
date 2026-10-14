package com.droiddeck.launcher.session

import com.droiddeck.launcher.core.PerfPresets
import com.droiddeck.launcher.core.ProtonSmartDefaults
import com.droiddeck.launcher.frontend.GameEula
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GameSessionTest {
    private class FakeHandle(override val label: String = "game") : GameProcessHandle {
        var alive = true
        var code: Int? = null
        override fun isAlive(): Boolean = alive
        override fun destroy() { alive = false; if (code == null) code = 143 }
        override fun destroyForcibly() { alive = false; if (code == null) code = 137 }
        override fun exitCode(): Int? = code
        fun die(code: Int) { alive = false; this.code = code }
    }

    private class FakeSpawner : GameProcessSpawner {
        val handles = mutableListOf<FakeHandle>()
        var lastEnv: Map<String, String> = emptyMap()
        override fun spawn(command: List<String>, env: Map<String, String>, workDir: File?): GameProcessHandle {
            lastEnv = env
            return FakeHandle().also { handles.add(it) }
        }
    }

    private fun session(spawner: FakeSpawner = FakeSpawner(), logTail: () -> List<String> = { listOf("t1", "t2") }): Pair<GameSession, GameSession.Files> {
        val root = Files.createTempDirectory("gsession").toFile()
        val files = GameSession.Files(
            eulaDir = File(root, "eula"),
            reportDir = File(root, "reports"),
            profileDir = File(root, "profiles"),
        )
        return GameSession(spawner, files, clock = { 1700000000000L }, logTail = logTail) to files
    }

    private fun request(profile: GameProfile? = null) = GameSession.LaunchRequest(
        appId = "620",
        command = listOf("wine", "game.exe"),
        baseEnv = mapOf("VKD3D_FEATURE_LEVEL" to "12_2", "VKD3D_SHADER_MODEL" to "6_9"),
        profile = profile,
        layer = ProtonSmartDefaults.TranslationLayer.DXVK_STOCK,
    )

    @Test fun launchIsGatedOnEula() {
        val (s, files) = session()
        val outcome = s.launch(request())
        assertTrue(outcome is GameSession.LaunchOutcome.EulaRequired)
        assertFalse(s.isRunning("620"))
        // Accepting unlocks the launch.
        GameEula.accept(files.eulaDir, "620")
        val started = s.launch(request())
        assertTrue(started is GameSession.LaunchOutcome.Started)
        assertTrue(s.isRunning("620"))
    }

    @Test fun launchAppliesTheMaliVerdictClamp() {
        val spawner = FakeSpawner()
        val (s, files) = session(spawner)
        GameEula.accept(files.eulaDir, "620")
        s.launch(request())
        // Upstream 12_2 default must never reach the game on this GPU.
        assertEquals("11_1", spawner.lastEnv["VKD3D_FEATURE_LEVEL"])
        assertEquals("6_0", spawner.lastEnv["VKD3D_SHADER_MODEL"])
    }

    @Test fun unexpectedDeathProducesACrashReport() {
        val spawner = FakeSpawner()
        val (s, files) = session(spawner)
        GameEula.accept(files.eulaDir, "620")
        s.launch(request())
        spawner.handles.single().die(139)
        val report = s.checkCrashed("620")
        assertNotNull(report)
        assertFalse(report!!.clean)
        assertEquals(listOf("t1", "t2"), report.logTail)
        assertTrue(File(files.reportDir, "crash-${report.sessionId}.json").isFile)
        assertFalse(s.isRunning("620"))
    }

    @Test fun cleanExitProducesNoReport() {
        val spawner = FakeSpawner()
        val (s, files) = session(spawner)
        GameEula.accept(files.eulaDir, "620")
        s.launch(request())
        spawner.handles.single().die(0)
        assertNull(s.checkCrashed("620"))
        assertEquals(0, files.reportDir.listFiles()?.size ?: 0)
    }

    @Test fun userInitiatedStopKillsWithoutACrashReport() {
        val spawner = FakeSpawner()
        val (s, files) = session(spawner)
        GameEula.accept(files.eulaDir, "620")
        s.launch(request())
        s.stop("620")
        assertFalse(s.isRunning("620"))
        assertEquals(0, files.reportDir.listFiles()?.size ?: 0)
        assertNull(s.checkCrashed("620"))
    }

    @Test fun closeLeavesNoOrphanedProcesses() {
        val spawner = FakeSpawner()
        val (s, files) = session(spawner)
        GameEula.accept(files.eulaDir, "620")
        GameEula.accept(files.eulaDir, "621")
        s.launch(request().copy(appId = "620"))
        s.launch(request().copy(appId = "621"))
        s.close()
        assertTrue(spawner.handles.all { !it.isAlive() })
        assertFalse(s.isRunning("620"))
        assertFalse(s.isRunning("621"))
    }

    @Test fun launchingTwiceReplacesTheOldSession() {
        val spawner = FakeSpawner()
        val (s, files) = session(spawner)
        GameEula.accept(files.eulaDir, "620")
        s.launch(request())
        val first = spawner.handles.single()
        s.launch(request())
        assertFalse(first.isAlive())
        assertEquals(2, spawner.handles.size)
        assertTrue(s.isRunning("620"))
    }

    @Test fun buildLaunchEnvComposesProfilePresetAndBase() {
        val profile = GameProfile(
            appId = "620",
            preset = PerfPresets.Preset.PERFORMANCE,
            fexFlags = mapOf("FEX_TSOENABLED" to "1"),
            envVars = mapOf("DXVK_HUD" to "fps"),
            frameLimiterFps = 90,
            overlay = PerfOverlay.OverlayState(enabled = true, showFps = true, showFrameTimes = true),
        )
        val env = GameSession.buildLaunchEnv(
            profile, ProtonSmartDefaults.TranslationLayer.DXVK_STOCK, mapOf("BASE" to "1"))
        assertEquals("1", env["BASE"])
        assertEquals("1", env["FEX_TSOENABLED"]) // explicit flag wins over PERFORMANCE preset's 0
        assertEquals("1", env["FEX_MULTIBLOCK"]) // PERFORMANCE preset
        assertEquals("dxvk.maxFrameRate = 90", env["DXVK_CONFIG"]) // explicit fps wins over preset 120
        assertEquals("fps,frametimes", env["DXVK_HUD"]) // overlay merged into profile env
        assertEquals("11_1", env["VKD3D_FEATURE_LEVEL"]) // Mali clamp always applied
    }

    @Test fun buildLaunchEnvUsesPresetDefaultsWhenProfileIsNull() {
        val env = GameSession.buildLaunchEnv(
            null, ProtonSmartDefaults.TranslationLayer.VKD3D_PROTON, emptyMap())
        assertEquals("60", env["VKD3D_FRAME_RATE"]) // Balanced preset default on vkd3d
        assertNull(env["DXVK_CONFIG"])
    }

    @Test fun effectiveCpuPinningPrefersExplicitProfilePinning() {
        val mapping = PerfPresets.for9300Plus(PerfPresets.Preset.BALANCED)
        assertEquals("4,5,6", GameSession.effectiveCpuPinning(GameProfile(appId = "620"), mapping))
        assertEquals("0,1", GameSession.effectiveCpuPinning(GameProfile(appId = "620", cpuPinning = "0,1"), mapping))
    }
}
