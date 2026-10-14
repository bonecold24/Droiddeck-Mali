package com.droiddeck.launcher.comptest

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds the compatibility-test report (schema v2) in JSON and Markdown.
 * Pure — no Android framework, fully host-unit-testable.
 *
 * Schema v2 adds: per-stage diagnostics (commandLine, envVars, exitCode,
 * outputTail, logFile, payloadInstall), driverValidation, and firstFailure.
 */
object CompatReport {
    const val SCHEMA_VERSION = 2

    /**
     * First failing stage in suite order. Skips passes and skips; returns null
     * when nothing failed. Pure; host-unit-tested.
     */
    fun firstFailure(results: List<TestResult>): FirstFailure? {
        for (r in results) {
            if (!r.outcome.isFailure) continue
            val why = StringBuilder(r.outcome.label())
            if (!r.note.isNullOrBlank()) why.append(": ").append(r.note)
            r.payloadInstall?.takeIf { !it.ok }?.let {
                why.append(" [payload: ").append(it.reason ?: "failed").append("]")
            }
            return FirstFailure(
                specId = r.specId,
                label = r.label,
                outcomeLabel = r.outcome.label(),
                reason = why.toString().take(600),
            )
        }
        return null
    }

    /** Attach the computed first failure to report data. Pure. */
    fun withFirstFailure(d: CompatReportData): CompatReportData =
        d.copy(firstFailure = firstFailure(d.results))

    fun toJson(d: CompatReportData): String {
        val env = d.environment
        val driver = env.driverPackage
        return Json.obj(
            "schemaVersion" to Json.num(d.schemaVersion),
            "generatedAtMs" to Json.num(d.generatedAtMs),
            "environment" to Json.obj(
                "appVersion" to Json.str(env.appVersion),
                "appCommit" to Json.str(env.appCommit),
                "driverPackage" to Json.obj(
                    "name" to Json.str(driver.name),
                    "version" to Json.str(driver.version),
                    "binarySha256" to Json.str(driver.binarySha256),
                    "vulkanApi" to Json.str(driver.vulkanApi),
                ),
                "driverValidation" to (env.driverValidation?.let {
                    Json.obj(
                        "selectedId" to Json.str(it.selectedId),
                        "usable" to Json.bool(it.usable),
                        "reason" to Json.str(it.reason),
                        "detail" to Json.str(it.detail),
                    )
                } ?: "null"),
                "protonVersion" to Json.str(env.protonVersion),
                "kbaseUapi" to Json.str(env.kbaseUapi),
                "deviceModel" to Json.str(env.deviceModel),
                "soc" to Json.str(env.soc),
            ),
            "firstFailure" to (d.firstFailure?.let {
                Json.obj(
                    "specId" to Json.str(it.specId),
                    "label" to Json.str(it.label),
                    "outcome" to Json.str(it.outcomeLabel),
                    "reason" to Json.str(it.reason),
                )
            } ?: "null"),
            "results" to Json.arr(d.results.map { r ->
                Json.obj(
                    "specId" to Json.str(r.specId),
                    "label" to Json.str(r.label),
                    "kind" to Json.str(r.kind.name),
                    "outcome" to Json.str(r.outcome.label()),
                    "startedAtMs" to Json.num(r.startedAtMs),
                    "durationMs" to Json.num(r.durationMs),
                    "avgFps" to Json.num(r.avgFps),
                    "onePercentLowFps" to Json.num(r.onePercentLowFps),
                    "maxFrameTimeMs" to Json.num(r.maxFrameTimeMs),
                    "telemetryStart" to telemetryJson(r.telemetryStart),
                    "telemetryEnd" to telemetryJson(r.telemetryEnd),
                    "driver" to (r.driver?.let {
                        Json.obj(
                            "name" to Json.str(it.name),
                            "version" to Json.str(it.version),
                            "binarySha256" to Json.str(it.binarySha256),
                            "vulkanApi" to Json.str(it.vulkanApi),
                        )
                    } ?: "null"),
                    "commandLine" to Json.str(r.commandLine),
                    "envVars" to (r.envVars?.let { m ->
                        Json.obj(*m.entries.map { (k, v) -> k to Json.str(v) }.toTypedArray())
                    } ?: "null"),
                    "exitCode" to Json.num(r.exitCode),
                    "outputTail" to (r.outputTail?.let { t ->
                        Json.arr(t.map { Json.str(it) })
                    } ?: "null"),
                    "outputLinesTotal" to Json.num(r.outputLinesTotal),
                    "stderrMerged" to Json.bool(r.stderrMerged),
                    "logFile" to Json.str(r.logFile),
                    "payloadInstall" to (r.payloadInstall?.let { p ->
                        Json.obj(
                            "ok" to Json.bool(p.ok),
                            "guestPath" to Json.str(p.guestPath),
                            "filesInstalled" to Json.arr(p.filesInstalled.map { Json.str(it) }),
                            "reason" to Json.str(p.reason),
                        )
                    } ?: "null"),
                    "note" to Json.str(r.note),
                )
            }),
        )
    }

    private fun telemetryJson(t: TelemetrySnapshot?): String = t?.let {
        Json.obj(
            "cpuTempC" to Json.num(it.cpuTempC),
            "gpuTempC" to Json.num(it.gpuTempC),
            "batteryPct" to Json.num(it.batteryPct),
            "ramUsedMb" to Json.num(it.ramUsedMb),
            "atMs" to Json.num(it.atMs),
        )
    } ?: "null"

    fun toMarkdown(d: CompatReportData): String {
        val env = d.environment
        val driver = env.driverPackage
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date(d.generatedAtMs))
        return buildString {
            appendLine("# DroidDeck Compatibility Test Report")
            appendLine()
            appendLine("Generated: $date")
            appendLine()
            // Plain-language verdict: which stage failed first and why (Gate A4).
            val ff = d.firstFailure
            if (ff != null) {
                appendLine("## First failure")
                appendLine()
                appendLine("**${cell(ff.label)}** — ${cell(ff.outcomeLabel)}")
                appendLine()
                appendLine(cell(ff.reason))
                appendLine()
            } else {
                appendLine("## First failure")
                appendLine()
                appendLine("None — every stage passed or was skipped.")
                appendLine()
            }
            appendLine("## Environment")
            appendLine()
            appendLine("| Component | Version |")
            appendLine("| --- | --- |")
            appendLine("| App | ${cell(env.appVersion)} (${cell(env.appCommit)}) |")
            appendLine("| GPU driver | ${cell(driver.name)} ${cell(driver.version)} |")
            appendLine("| Driver binary SHA-256 | `${cell(driver.binarySha256)}` |")
            appendLine("| Vulkan API | ${cell(driver.vulkanApi)} |")
            appendLine("| Proton | ${cell(env.protonVersion)} |")
            appendLine("| kbase UAPI | ${cell(env.kbaseUapi)} |")
            appendLine("| Device | ${cell(env.deviceModel)} |")
            appendLine("| SoC | ${cell(env.soc)} |")
            env.driverValidation?.let { v ->
                appendLine("| Linux driver validation | ${if (v.usable) "usable" else "NOT USABLE"}: ${cell(v.detail)} |")
            }
            appendLine()
            appendLine("## Results")
            appendLine()
            appendLine("| Test | Outcome | Exit | Avg FPS | 1% low | Max frame (ms) | CPU °C | GPU °C | Batt % | Note |")
            appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
            for (r in d.results) {
                appendLine(
                    "| ${cell(r.label)} | ${cell(r.outcome.label())} | ${r.exitCode?.toString() ?: "—"} | " +
                        "${num(r.avgFps)} | ${num(r.onePercentLowFps)} | " +
                        "${num(r.maxFrameTimeMs)} | ${num(r.telemetryEnd?.cpuTempC)} | ${num(r.telemetryEnd?.gpuTempC)} | " +
                        "${r.telemetryEnd?.batteryPct?.toString() ?: "—"} | ${cell(r.note ?: "")} |",
                )
            }
            appendLine()
            // Diagnostics for failed stages: command, env, log file, output tail.
            val failed = d.results.filter { it.outcome.isFailure }
            if (failed.isNotEmpty()) {
                appendLine("## Stage diagnostics (failed stages)")
                appendLine()
                for (r in failed) {
                    appendLine("### ${cell(r.label)} — ${cell(r.outcome.label())}")
                    appendLine()
                    r.commandLine?.let { appendLine("Command: `${cell(it)}`") }
                    r.envVars?.takeIf { it.isNotEmpty() }?.let { m ->
                        appendLine("Env: ${m.entries.joinToString(" ") { (k, v) -> "$k=${cell(v)}" }}")
                    }
                    r.logFile?.let {
                        appendLine("Full log: `/data/data/com.droiddeck.launcher/files/logs/tools/${cell(it)}`")
                    }
                    r.payloadInstall?.let { p ->
                        if (!p.ok) appendLine("Payload install FAILED at ${cell(p.guestPath)}: ${cell(p.reason ?: "?")}")
                    }
                    val tail = r.outputTail?.takeLast(20)?.filter { it.isNotBlank() }
                    if (!tail.isNullOrEmpty()) {
                        appendLine("Last output lines:")
                        appendLine("```")
                        tail.forEach { appendLine(it.take(300)) }
                        appendLine("```")
                    }
                    appendLine()
                }
            }
            appendLine("Schema version: $SCHEMA_VERSION. Telemetry is best-effort (sysfs may be unreadable on some devices).")
            appendLine("Per-stage full logs are in the share ZIP (see CompatTestExport).")
        }
    }

    private fun cell(s: String): String = s.replace("|", "\\|").replace("\n", " ")
    private fun num(v: Double?): String = v?.let { "%.1f".format(Locale.US, it) } ?: "—"
}
