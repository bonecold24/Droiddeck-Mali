package com.droiddeck.launcher.session

import com.droiddeck.launcher.core.MiniJson
import java.io.File

/** How a game process died. */
sealed interface ProcessExit {
    data class Code(val code: Int) : ProcessExit
    data class Signal(val name: String) : ProcessExit
    object Unknown : ProcessExit
}

/**
 * Crash report: game process death + the last log tail. Written on abnormal
 * session end; clean exits (code 0) produce no report file unless asked.
 */
data class GameCrashReport(
    val appId: String,
    val sessionId: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val exit: ProcessExit,
    val logTail: List<String>,
) {
    companion object {
        const val MAX_TAIL_LINES = 400

        /** Entry point: caps the tail so a huge log cannot blow up the report. */
        fun build(
            appId: String,
            sessionId: String,
            startedAtMs: Long,
            endedAtMs: Long,
            exit: ProcessExit,
            tail: List<String>,
        ): GameCrashReport = GameCrashReport(
            appId, sessionId, startedAtMs, endedAtMs, exit, tail.takeLast(MAX_TAIL_LINES),
        )
    }

    val clean: Boolean get() = exit is ProcessExit.Code && exit.code == 0

    fun toText(): String = buildString {
        appendLine("Game crash report")
        appendLine("appId: $appId")
        appendLine("session: $sessionId")
        appendLine("started: $startedAtMs ended: $endedAtMs")
        appendLine(when (val e = exit) {
            is ProcessExit.Code -> "exit code ${e.code}"
            is ProcessExit.Signal -> "killed by signal ${e.name}"
            ProcessExit.Unknown -> "exit status unknown"
        })
        appendLine("clean: $clean")
        appendLine("--- last ${logTail.size} log lines ---")
        logTail.forEach { appendLine(it) }
    }

    fun toJson(): String = MiniJson.stringify(linkedMapOf<String, Any?>(
        "appId" to appId,
        "sessionId" to sessionId,
        "startedAtMs" to startedAtMs,
        "endedAtMs" to endedAtMs,
        "clean" to clean,
        "exit" to when (val e = exit) {
            is ProcessExit.Code -> mapOf("code" to e.code.toLong())
            is ProcessExit.Signal -> mapOf("signal" to e.name)
            ProcessExit.Unknown -> mapOf("unknown" to true)
        },
        "logTail" to logTail,
    ))
}

object GameCrashReportStore {
    private val sessionIdPattern = Regex("[A-Za-z0-9_-]{1,64}")

    fun write(dir: File, report: GameCrashReport): File {
        require(sessionIdPattern.matches(report.sessionId)) { "bad sessionId ${report.sessionId}" }
        dir.mkdirs()
        val json = File(dir, "crash-${report.sessionId}.json")
        val text = File(dir, "crash-${report.sessionId}.txt")
        json.writeText(report.toJson())
        text.writeText(report.toText())
        return json
    }
}
