package com.droiddeck.launcher.core

/** Bounded in-memory ring of recent log lines; backs crash-report tails. */
class LogTailBuffer(private val capacity: Int = 400) {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(line: String) {
        if (capacity <= 0) return
        lines.addLast(line)
        while (lines.size > capacity) lines.removeFirst()
    }

    @Synchronized
    fun tail(max: Int): List<String> {
        if (max <= 0) return emptyList()
        return lines.takeLast(minOf(max, lines.size)).toList()
    }

    @Synchronized
    fun clear() = lines.clear()
}
