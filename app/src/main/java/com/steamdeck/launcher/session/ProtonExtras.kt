package com.steamdeck.launcher.session

import android.content.Context
import com.steamdeck.launcher.core.FileUtils
import com.steamdeck.launcher.runtime.LinuxRuntime
import java.io.File

/**
 * Third-party Proton builds for the client - GE-Proton and proton-cachyos, both published as
 * native ARM64 tarballs. The runtime's own registrar (bannerlator-proton-extra) downloads and
 * installs one into the client's compatibilitytools.d and patches it past pressure-vessel; this
 * side only leaves it the request, one line in ~/.bl-proton-extra, which the session script reads
 * before the client starts. A line is dropped once it has succeeded; a failed download stays and
 * is retried next session. Hundreds of megabytes each, so only ever on request.
 */
object ProtonExtras {
    class Tool(val id: String, val name: String, val prefix: String)

    val tools = listOf(
        Tool("ge", "GE-Proton", "GE-Proton"),
        Tool("cachyos", "proton-cachyos", "proton-cachyos"),
    )

    private fun home(context: Context) = File(LinuxRuntime.rootDir(context), "root")
    private fun requests(context: Context) = File(home(context), ".bl-proton-extra")
    private fun toolsDir(context: Context) =
        File(home(context), ".local/share/Steam/compatibilitytools.d")

    /** The installed build's directory name (e.g. GE-Proton11-7), or null. */
    fun installed(context: Context, tool: Tool): String? =
        toolsDir(context).listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(tool.prefix) && File(it, "toolmanifest.vdf").isFile }
            ?.maxByOrNull { it.name }?.name

    fun queued(context: Context, tool: Tool): Boolean =
        requestLines(context).any { it.trim() == tool.id || it.trim().startsWith(tool.id + " ") }

    fun queue(context: Context, tool: Tool) {
        if (queued(context, tool)) return
        val lines = requestLines(context) + tool.id
        FileUtils.writeString(requests(context), lines.joinToString("\n") + "\n")
    }

    fun unqueue(context: Context, tool: Tool) {
        val lines = requestLines(context).filterNot { it.trim() == tool.id || it.trim().startsWith(tool.id + " ") }
        FileUtils.writeString(requests(context), if (lines.isEmpty()) "" else lines.joinToString("\n") + "\n")
    }

    /** Deletes the installed build; the client forgets it on its next start. */
    fun remove(context: Context, tool: Tool) {
        installed(context, tool)?.let { FileUtils.delete(File(toolsDir(context), it)) }
    }

    private fun requestLines(context: Context): List<String> =
        FileUtils.readString(requests(context))?.lines()?.filter { it.isNotBlank() } ?: emptyList()
}
