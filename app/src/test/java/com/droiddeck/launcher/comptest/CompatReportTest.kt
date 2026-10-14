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
        outcome = TestOutcome.REACHED_MENU, startedAtMs = 1_700_000_000_000L, durationMs = 12_000L,
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
            "\"schemaVersion\": 1",
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
}
