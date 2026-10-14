package com.droiddeck.launcher.comptest

import org.junit.Assert.assertTrue
import org.junit.Test

class CompatReportTest {
    private fun env() = EnvironmentInfo(
        appVersion = "2.3.1",
        appCommit = "abcdef1234",
        driverPackage = DriverIdentity(
            name = "PanVK Kbase",
            version = "0.1.0-rc3-universal+local1",
            binarySha256 = "0123456789abcdef".repeat(4),
            vulkanApi = "1.4.363",
        ),
        protonVersion = "Proton-EM 10",
        kbaseUapi = "1.21",
        deviceModel = "Xiaomi 14T Pro",
        soc = "Dimensity 9300+",
    )

    private fun result() = TestResult(
        specId = "vkcube", label = "vkcube (Vulkan cube)", kind = TestKind.SYSTEM_CHECK,
        outcome = TestOutcome.ReachedMenu, startedAtMs = 1_700_000_000_000L, durationMs = 12_000L,
        avgFps = 60.0, onePercentLowFps = 59.5, maxFrameTimeMs = 18.2,
        telemetryStart = TelemetrySnapshot(45.0, 50.5, 80, 2_048L, 1_700_000_000_000L),
        telemetryEnd = TelemetrySnapshot(47.0, 53.0, 79, 2_100L, 1_700_000_012_000L),
        driver = env().driverPackage,
        note = "rendered \"fine\" <ok>",
    )

    private fun data() = CompatReportData(
        generatedAtMs = 1_700_000_012_000L,
        environment = env(),
        results = listOf(result()),
    )

    @Test fun jsonContainsExactComponentVersions() {
        val json = CompatReport.toJson(data())
        for (needle in listOf(
            "\"schemaVersion\": 2",
            "\"appVersion\": \"2.3.1\"",
            "\"appCommit\": \"abcdef1234\"",
            "\"name\": \"PanVK Kbase\"",
            "\"version\": \"0.1.0-rc3-universal+local1\"",
            "\"vulkanApi\": \"1.4.363\"",
            "\"protonVersion\": \"Proton-EM 10\"",
            "\"kbaseUapi\": \"1.21\"",
            "\"deviceModel\": \"Xiaomi 14T Pro\"",
            "\"soc\": \"Dimensity 9300+\"",
            "\"outcome\": \"REACHED_MENU\"",
            "\"avgFps\": 60.0",
        )) assertTrue("missing: $needle", json.contains(needle))
        assertTrue(json.contains("0123456789abcdef".repeat(4)))
    }

    @Test fun jsonEscapesSpecialCharacters() {
        val json = CompatReport.toJson(data())
        assertTrue(json.contains("\\\"fine\\\""))
        assertTrue("control chars must be escaped", !json.contains("\"note\": \"rendered \""))
    }

    @Test fun jsonHandlesNullsGracefully() {
        val r = result().copy(avgFps = null, telemetryStart = null, note = null)
        val json = CompatReport.toJson(data().copy(results = listOf(r)))
        assertTrue(json.contains("\"avgFps\": null"))
        assertTrue(json.contains("\"telemetryStart\": null"))
        assertTrue(json.contains("\"note\": null"))
    }

    @Test fun markdownContainsVersionsAndResultsTable() {
        val md = CompatReport.toMarkdown(data())
        for (needle in listOf(
            "# DroidDeck Compatibility Test Report",
            "0.1.0-rc3-universal+local1",
            "1.4.363",
            "Proton-EM 10",
            "kbase UAPI",
            "Xiaomi 14T Pro",
            "vkcube (Vulkan cube)",
            "REACHED_MENU",
            "60.0",
            "47.0",
        )) assertTrue("missing: $needle", md.contains(needle))
    }

    @Test fun markdownEscapesPipesInCells() {
        val r = result().copy(label = "weird | label")
        val md = CompatReport.toMarkdown(data().copy(results = listOf(r)))
        assertTrue(md.contains("weird \\| label"))
    }

    @Test fun schemaV2SerializesDiagnostics() {
        val r = result().copy(
            outcome = TestOutcome.FailExit(1),
            commandLine = "/usr/bin/vkcube --c 600",
            envVars = mapOf("VK_DRIVER_FILES" to "/x/panfrost_icd.aarch64.json"),
            exitCode = 1,
            outputTail = listOf("line1", "line2"),
            outputLinesTotal = 150,
            stderrMerged = true,
            logFile = "comptest-vkcube.log",
            payloadInstall = PayloadInstallResult(false, reason = "assets empty"),
        )
        val json = CompatReport.toJson(data().copy(results = listOf(r)))
        for (needle in listOf(
            "\"schemaVersion\": 2",
            "\"outcome\": \"FAIL_EXIT(1)\"",
            "\"commandLine\": \"/usr/bin/vkcube --c 600\"",
            "\"VK_DRIVER_FILES\": \"/x/panfrost_icd.aarch64.json\"",
            "\"exitCode\": 1",
            "\"outputTail\": [\"line1\",\"line2\"]",
            "\"outputLinesTotal\": 150",
            "\"stderrMerged\": true",
            "\"logFile\": \"comptest-vkcube.log\"",
            "\"reason\": \"assets empty\"",
        )) assertTrue("missing: $needle", json.contains(needle))
    }

    @Test fun markdownRendersFirstFailureSection() {
        val r = result().copy(
            outcome = TestOutcome.FailExit(1),
            note = "exit=1 — last output: boom",
        )
        val md = CompatReport.toMarkdown(CompatReport.withFirstFailure(data().copy(results = listOf(r))))
        assertTrue(md.contains("## First failure"))
        assertTrue(md.contains("FAIL_EXIT(1)"))
        assertTrue(md.contains("boom"))
        assertTrue(md.contains("## Stage diagnostics (failed stages)"))
    }

    @Test fun markdownSaysNoneWhenNothingFailed() {
        val md = CompatReport.toMarkdown(CompatReport.withFirstFailure(data()))
        assertTrue(md.contains("None — every stage passed or was skipped."))
    }
}
