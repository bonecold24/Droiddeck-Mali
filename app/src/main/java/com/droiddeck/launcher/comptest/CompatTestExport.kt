package com.droiddeck.launcher.comptest

import android.content.Context
import com.droiddeck.launcher.frontend.AddedGames
import java.io.File

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
 * ResultsSink), so a killed run still keeps report.json/report.md up to date.
 * Sharing copies the latest pair into the FileProvider-exposed cache dir and
 * fires an ACTION_SEND_MULTIPLE share sheet. No login needed.
 */
object CompatTestExport {
    const val DIR = "comptest"
    const val JSON_NAME = "report.json"
    const val MD_NAME = "report.md"

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
        writeAtomic(json, CompatReport.toJson(data))
        writeAtomic(md, CompatReport.toMarkdown(data))
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

    /** One-tap share: copies the latest report pair to the share cache and opens the sheet. */
    fun shareLatest(context: Context, runId: String) {
        val dir = runDir(context, runId)
        val json = File(dir, JSON_NAME)
        val md = File(dir, MD_NAME)
        if (!json.isFile || !md.isFile) return
        val shareDir = File(File(context.cacheDir, "share"), "comptest-$runId").apply { mkdirs() }
        val jsonCopy = File(shareDir, "droiddeck-comptest-$runId.json").also { json.copyTo(it, overwrite = true) }
        val mdCopy = File(shareDir, "droiddeck-comptest-$runId.md").also { md.copyTo(it, overwrite = true) }
        val authority = "${context.packageName}.logs"
        val uris = listOf(jsonCopy, mdCopy).map {
            androidx.core.content.FileProvider.getUriForFile(context, authority, it)
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "DroidDeck compatibility test report")
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share test report"))
    }
}
