package com.droiddeck.launcher.comptest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gate A4: the report must say plainly which stage failed first and why. */
class FirstFailureTest {

    private fun result(
        id: String,
        outcome: TestOutcome,
        note: String? = null,
        payload: PayloadInstallResult? = null,
    ) = TestResult(
        specId = id, label = id, kind = TestKind.SYSTEM_CHECK, outcome = outcome,
        startedAtMs = 0L, durationMs = 100L, avgFps = null, onePercentLowFps = null,
        maxFrameTimeMs = null, telemetryStart = null, telemetryEnd = null,
        driver = null, note = note, payloadInstall = payload,
    )

    @Test fun noFailureWhenAllPassOrSkipped() {
        val results = listOf(
            result("stage0-env", TestOutcome.Pass),
            result("vkcube", TestOutcome.Pass),
            result("game", TestOutcome.Skipped),
        )
        assertNull(CompatReport.firstFailure(results))
    }

    @Test fun picksFirstFailingStageInOrder() {
        val results = listOf(
            result("stage0-env", TestOutcome.Pass),
            result("vkcube", TestOutcome.FailExit(1), "exit=1 — last output: boom"),
            result("vulkaninfo", TestOutcome.NotFound),
        )
        val ff = CompatReport.firstFailure(results)!!
        assertEquals("vkcube", ff.specId)
        assertEquals("FAIL_EXIT(1)", ff.outcomeLabel)
        assertTrue(ff.reason.contains("boom"))
    }

    @Test fun prereqMissingCountsAsFailure() {
        val results = listOf(
            result("stage0-env", TestOutcome.Pass),
            result("vkcube", TestOutcome.PrereqMissing("bionic driver selected")),
        )
        val ff = CompatReport.firstFailure(results)!!
        assertEquals("vkcube", ff.specId)
        assertEquals("PREREQ_MISSING", ff.outcomeLabel)
    }

    @Test fun payloadReasonIsAppended() {
        val results = listOf(
            result(
                "d3d9-cube", TestOutcome.PrereqMissing("payload failed"),
                payload = PayloadInstallResult(false, reason = "assets/comptest empty"),
            ),
        )
        val ff = CompatReport.firstFailure(results)!!
        assertTrue("payload reason must appear: ${ff.reason}", ff.reason.contains("assets/comptest empty"))
    }

    @Test fun signalOutcomeLabel() {
        val results = listOf(result("vkcube", TestOutcome.Signal(11)))
        assertEquals("SIGNAL(11)", CompatReport.firstFailure(results)!!.outcomeLabel)
    }

    @Test fun withFirstFailureAttachesToData() {
        val env = EnvironmentInfo(
            appVersion = "v", appCommit = "c",
            driverPackage = DriverIdentity("d", "1", "sha", "api"),
            protonVersion = "p", kbaseUapi = "u", deviceModel = "m", soc = "s",
        )
        val data = CompatReportData(
            generatedAtMs = 0L, environment = env,
            results = listOf(result("vkcube", TestOutcome.FailExit(1), "nope")),
        )
        val withFf = CompatReport.withFirstFailure(data)
        assertEquals("vkcube", withFf.firstFailure!!.specId)
        // JSON + Markdown both render the verdict.
        assertTrue(CompatReport.toJson(withFf).contains("\"specId\": \"vkcube\""))
        assertTrue(CompatReport.toMarkdown(withFf).contains("## First failure"))
    }
}
