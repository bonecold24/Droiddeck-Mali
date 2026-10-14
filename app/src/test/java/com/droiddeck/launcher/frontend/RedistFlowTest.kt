package com.droiddeck.launcher.frontend

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RedistFlowTest {
    private fun gameDirWith(vararg depots: String): File {
        val steamapps = Files.createTempDirectory("steamapps").toFile()
        val gameDir = File(steamapps, "common/620").apply { mkdirs() }
        val block = depots.joinToString("\n") { "\"$it\"\t\t\"1\"" }
        File(steamapps, "appmanifest_620.acf").writeText(
            "\"AppState\"\n{\n\t\"appid\"\t\t\"620\"\n\t\"SharedDepots\"\n\t{\n$block\n\t}\n}",
        )
        return gameDir
    }

    private class FakeInstaller(
        val installed: MutableSet<String> = mutableSetOf(),
        val failOn: Set<String> = emptySet(),
    ) : RedistFlow.Installer {
        val attempts = mutableListOf<String>()
        override fun isInstalled(component: String): Boolean = component in installed
        override fun install(component: String, onProgress: (Int) -> Unit): String? {
            attempts.add(component)
            onProgress(50)
            onProgress(100)
            return if (component in failOn) "boom" else {
                installed.add(component)
                null
            }
        }
    }

    @Test fun planDetectsOnlyMissingRedists() {
        val installer = FakeInstaller(installed = mutableSetOf("vcredist2022_dll"))
        val steps = RedistFlow.plan(gameDirWith("228989", "228990"), 620, installer)
        assertEquals(listOf("d3dx9"), steps.map { it.component })
        assertEquals("DirectX (June 2010)", steps.single().label)
    }

    @Test fun planIsEmptyWhenManifestIsMissing() {
        val dir = Files.createTempDirectory("nogame").toFile()
        assertTrue(RedistFlow.plan(dir, 620, FakeInstaller()).isEmpty())
        assertTrue(RedistFlow.plan(null, 620, FakeInstaller()).isEmpty())
    }

    @Test fun runInstallsAllStepsAndReportsSuccess() {
        val installer = FakeInstaller()
        val steps = RedistFlow.plan(gameDirWith("228989", "228990"), 620, installer)
        val seen = mutableListOf<Pair<String, RedistFlow.StepState>>()
        assertTrue(RedistFlow.run(steps, installer, onStep = { seen.add(it.component to it.state) }))
        assertEquals(listOf("vcredist2022_dll", "d3dx9"), installer.attempts)
        assertTrue(steps.all { it.state == RedistFlow.StepState.DONE })
        assertTrue(seen.any { it.second == RedistFlow.StepState.INSTALLING })
    }

    @Test fun runContinuesPastFailuresAndReportsFalse() {
        val installer = FakeInstaller(failOn = setOf("vcredist2022_dll"))
        val steps = RedistFlow.plan(gameDirWith("228989", "228990"), 620, installer)
        assertFalse(RedistFlow.run(steps, installer))
        assertEquals(RedistFlow.StepState.FAILED, steps[0].state)
        assertEquals("boom", steps[0].error)
        assertEquals(RedistFlow.StepState.DONE, steps[1].state)
    }

    @Test fun alreadyInstalledComponentsAreSkipped() {
        val installer = FakeInstaller(installed = mutableSetOf("vcredist2022_dll", "d3dx9"))
        val steps = RedistFlow.plan(gameDirWith("228989", "228990"), 620, installer)
        assertTrue(steps.isEmpty())
    }

    @Test fun progressReachesTheListener() {
        val installer = FakeInstaller()
        val steps = RedistFlow.plan(gameDirWith("228990"), 620, installer)
        val progress = mutableListOf<Int>()
        RedistFlow.run(steps, installer, onProgress = { _, pct -> progress.add(pct) })
        assertTrue(progress.contains(100))
    }
}
