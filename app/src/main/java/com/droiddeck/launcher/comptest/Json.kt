package com.droiddeck.launcher.comptest

/** Minimal JSON writer (no dependency) used by the compat-test report. Pure. */
object Json {
    fun quote(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    /** Raw value pairs; values must already be valid JSON fragments (use [quote]/[num]/[bool]). */
    fun obj(vararg pairs: Pair<String, String>): String =
        pairs.joinToString(",", "{", "}") { (k, v) -> "${quote(k)}: $v" }

    fun arr(items: List<String>): String = items.joinToString(",", "[", "]")

    fun str(v: String?): String = if (v == null) "null" else quote(v)
    fun num(v: Number?): String = v?.toString() ?: "null"
    fun bool(v: Boolean?): String = v?.toString() ?: "null"
}
