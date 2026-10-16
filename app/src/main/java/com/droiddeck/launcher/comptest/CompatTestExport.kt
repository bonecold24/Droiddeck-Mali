package com.droiddeck.launcher.comptest

import android.content.Context
import com.droiddeck.launcher.frontend.AddedGames
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Discovers games actually installed on the device for the compat suite.
 * No Steam login required: uses the locally-scanned added-games library.
 * Missing games are simply absent here — the suite skips them gracefully.
 */
object CompatTestGames {
    fun listInstalled(context: Context): List<CompatTestSuite.InstalledGame> {
        return runCatching {
            AddedGames.scan(context)
                .filter { it.exe.isFile || it.exe.exists() }
                .map { CompatTestSuite.InstalledGame(gameId = it.gameId, name = it.name) }
                .distinctBy { it.gameId }
        }.getOrDefault(emptyList())
    }
}

/**
 * Report persistence + sharing.
 *
 * Partial results are written after every finished test (via the runner's
 * ResultsSink), so a killed run keeps report.json/report.md up to date.
 *
 * Sharing (Gate B1) builds a single ZIP containing the report JSON + Markdown
 * plus every per-stage log file, and shares that one file:
 *   droiddeck-comptest-<runId>.zip
 *     ├── droiddeck-comptest-<runId>.json
 *     ├── droiddeck-comptest-<runId>.md
 *     └── logs/
 *         ├── comptest-stage0-env.log   (full output: Android + guest sections)
 *         ├── comptest-vk-compute-test.log
 *         ├── comptest-vkcube.log
 *         ├── comptest-vulkaninfo.log
 *         ├── comptest-d3d9-cube.log
 *         ├── comptest-d3d11-cube.log
 *         ├── comptest-driver-install.log   (direct Tier-0 driver install)
 *         └── comptest-driver-import.log   (driver-manager import trace)
 *
 * Directive #10 task 4: the zip contains ONLY this run's logs, from
 * /data/data/<package>/files/comptest/<runId>/logs/. The old shared
 * <filesDir>/logs/tools/ dir is never zipped, so stale logs from previous
 * runs cannot leak in.
 *
 * That file's content (from the GuestCommand.run code path):
 *   == <Date string> /usr/bin/vulkaninfo
 *   <every line of merged stdout+stderr from the proot'd process>
 * The first line is the exact guest argv; stderr is merged into stdout
 * (ProcessBuilder.redirectErrorStream(true)), so the log is the complete output.
 */
object CompatTestExport {
    const val DIR = "comptest"
    const val JSON_NAME = "report.json"
    const val MD_NAME = "report.md"

    /** Run-scoped log dir: <filesDir>/comptest/<runId>/logs/. */
    fun runLogDir(context: Context, runId: String): File =
        File(runDir(context, runId), "logs").apply { mkdirs() }

    /**
     * Per-stage log files for this run, in suite order.
     * Directive #10 task 4: reads ONLY this run's log dir
     * (<filesDir>/comptest/<runId>/logs/) — never the shared tools dir, so
     * stale logs from previous runs cannot leak into the zip.
     * Pure wrt ordering; file existence is checked on-device.
     */
    fun stageLogFiles(context: Context, runId: String): List<File> {
        val dir = File(runDir(context, runId), "logs")
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.sortedBy { it.name } ?: emptyList()
    }

    fun runDir(context: Context, runId: String): File =
        File(File(context.filesDir, DIR), runId).apply { mkdirs() }

    fun latestRunDir(context: Context): File? =
        File(context.filesDir, DIR).listFiles { f -> f.isDirectory }
            ?.maxByOrNull { it.lastModified() }

    /** Writes JSON + Markdown atomically (temp file + rename). Returns the two files. */
    fun writeReport(context: Context, runId: String, data: CompatReportData): Pair<File, File> {
        val dir = runDir(context, runId)
        val json = File(dir, JSON_NAME)
        val md = File(dir, MD_NAME)
        writeAtomic(json, CompatReport.toJson(CompatReport.withFirstFailure(data)))
        writeAtomic(md, CompatReport.toMarkdown(CompatReport.withFirstFailure(data)))
        return json to md
    }

    private fun writeAtomic(target: File, content: String) {
        val tmp = File(target.parent, target.name + ".tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            // renameTo can fail across edge cases; fall back to a direct write.
            target.writeText(content)
            tmp.delete()
        }
    }

    /**
     * One-tap share (Gate B1): zips report.json + report.md + every per-stage
     * log into droiddeck-comptest-<runId>.zip and shares the single file.
     */
    fun shareLatest(context: Context, runId: String, specIds: List<String> = emptyList()) {
        val dir = runDir(context, runId)
        val json = File(dir, JSON_NAME)
        val md = File(dir, MD_NAME)
        if (!json.isFile || !md.isFile) return
        val shareDir = File(File(context.cacheDir, "share"), "comptest-$runId").apply { mkdirs() }
        val zipFile = File(shareDir, "droiddeck-comptest-$runId.zip")
        buildShareZip(zipFile, runId, json, md, stageLogFiles(context, runId))
        val authority = "${context.packageName}.logs"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, zipFile)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "DroidDeck compatibility test report")
            putExtra(
                android.content.Intent.EXTRA_TEXT,
                "DroidDeck Tier-0 report (JSON + Markdown + per-stage logs).",
            )
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share test report"))
    }

    /** Pure zip assembly, host-testable via temp dirs. */
    internal fun buildShareZip(
        zipFile: File,
        runId: String,
        json: File,
        md: File,
        stageLogs: List<File>,
    ) {
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            fun add(name: String, file: File) {
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            add("droiddeck-comptest-$runId.json", json)
            add("droiddeck-comptest-$runId.md", md)
            for (log in stageLogs) {
                add("logs/${log.name}", log)
            }
        }
    }
}
