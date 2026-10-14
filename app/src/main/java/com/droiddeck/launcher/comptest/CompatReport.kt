package com.droiddeck.launcher.comptest

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds the compatibility-test report (schema v1) in JSON and Markdown.
 * Pure — no Android framework, fully host-unit-testable.
 */
object CompatReport {
    const val SCHEMA_VERSION = 1

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
                "protonVersion" to Json.str(env.protonVersion),
                "kbaseUapi" to Json.str(env.kbaseUapi),
                "deviceModel" to Json.str(env.deviceModel),
                "soc" to Json.str(env.soc),
            ),
            "results" to Json.arr(d.results.map { r ->
                Json.obj(
                    "specId" to Json.str(r.specId),
                    "label" to Json.str(r.label),
                    "kind" to Json.str(r.kind.name),
                    "outcome" to Json.str(r.outcome.name),
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
            appendLine()
            appendLine("## Results")
            appendLine()
            appendLine("| Test | Outcome | Avg FPS | 1% low | Max frame (ms) | CPU °C | GPU °C | Batt % | Note |")
            appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- |")
            for (r in d.results) {
                appendLine(
                    "| ${cell(r.label)} | ${r.outcome.name} | ${num(r.avgFps)} | ${num(r.onePercentLowFps)} | " +
                        "${num(r.maxFrameTimeMs)} | ${num(r.telemetryEnd?.cpuTempC)} | ${num(r.telemetryEnd?.gpuTempC)} | " +
                        "${r.telemetryEnd?.batteryPct?.toString() ?: "—"} | ${cell(r.note ?: "")} |",
                )
            }
            appendLine()
            appendLine("Schema version: $SCHEMA_VERSION. Telemetry is best-effort (sysfs may be unreadable on some devices).")
        }
    }

    private fun cell(s: String): String = s.replace("|", "\\|")
    private fun num(v: Double?): String = v?.let { "%.1f".format(Locale.US, it) } ?: "—"
}
