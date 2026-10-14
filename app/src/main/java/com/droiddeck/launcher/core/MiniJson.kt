package com.droiddeck.launcher.core

/**
 * Minimal JSON reader/writer for small app-owned config files (per-game profiles,
 * EULA records). Pure Kotlin, no Android dependency, so it is unit-testable on a
 * plain host JVM. Not a general-purpose parser: it rejects duplicate keys and
 * non-finite numbers.
 */
object MiniJson {
    class JsonException(message: String) : IllegalArgumentException(message)

    fun parse(text: String): Any? {
        val p = Parser(text)
        val value = p.readValue()
        p.skipWs()
        if (!p.atEnd()) throw JsonException("trailing data at offset ${p.pos}")
        return value
    }

    fun stringify(value: Any?): String = buildString { appendValue(value) }

    private fun StringBuilder.appendValue(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> appendQuoted(value)
            is Boolean -> append(if (value) "true" else "false")
            is Long, is Int -> append(value.toString())
            is Double -> {
                if (!value.isFinite()) throw JsonException("non-finite number")
                append(value.toString())
            }
            is Float -> {
                if (!value.isFinite()) throw JsonException("non-finite number")
                append(value.toString())
            }
            is Map<*, *> -> {
                append('{')
                value.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) append(',')
                    if (k !is String) throw JsonException("object keys must be strings")
                    appendQuoted(k)
                    append(':')
                    appendValue(v)
                }
                append('}')
            }
            is List<*> -> {
                append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) append(',')
                    appendValue(v)
                }
                append(']')
            }
            else -> throw JsonException("unsupported value type ${value::class}")
        }
    }

    private fun StringBuilder.appendQuoted(s: String) {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }

    private class Parser(val text: String) {
        var pos = 0
        fun atEnd() = pos >= text.length
        fun skipWs() { while (!atEnd() && text[pos] in " \t\n\r") pos++ }
        fun readValue(): Any? {
            skipWs()
            if (atEnd()) throw JsonException("unexpected end of input")
            return when (val c = text[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                '-', in '0'..'9' -> readNumber()
                else -> throw JsonException("unexpected character '$c' at offset $pos")
            }
        }

        private fun readLiteral(word: String, value: Any?): Any? {
            if (!text.startsWith(word, pos)) throw JsonException("bad literal at offset $pos")
            pos += word.length
            return value
        }

        private fun readObject(): Map<String, Any?> {
            pos++ // {
            val out = linkedMapOf<String, Any?>()
            skipWs()
            if (!atEnd() && text[pos] == '}') { pos++; return out }
            while (true) {
                skipWs()
                if (atEnd() || text[pos] != '"') throw JsonException("expected string key at offset $pos")
                val key = readString()
                if (key in out) throw JsonException("duplicate key \"$key\"")
                skipWs()
                if (atEnd() || text[pos] != ':') throw JsonException("expected ':' at offset $pos")
                pos++
                out[key] = readValue()
                skipWs()
                if (atEnd()) throw JsonException("unterminated object")
                when (text[pos]) {
                    ',' -> { pos++; continue }
                    '}' -> { pos++; return out }
                    else -> throw JsonException("expected ',' or '}' at offset $pos")
                }
            }
        }

        private fun readArray(): List<Any?> {
            pos++ // [
            val out = mutableListOf<Any?>()
            skipWs()
            if (!atEnd() && text[pos] == ']') { pos++; return out }
            while (true) {
                out.add(readValue())
                skipWs()
                if (atEnd()) throw JsonException("unterminated array")
                when (text[pos]) {
                    ',' -> { pos++; continue }
                    ']' -> { pos++; return out }
                    else -> throw JsonException("expected ',' or ']' at offset $pos")
                }
            }
        }

        private fun readString(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("unterminated string")
                val c = text[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonException("unterminated escape")
                        when (val e = text[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) throw JsonException("bad unicode escape")
                                val hex = text.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16) ?: throw JsonException("bad unicode escape \\u$hex")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw JsonException("bad escape '\\$e'")
                        }
                    }
                    else -> {
                        if (c < ' ') throw JsonException("unescaped control character")
                        sb.append(c)
                    }
                }
            }
        }

        private fun readNumber(): Any {
            val start = pos
            if (!atEnd() && text[pos] == '-') pos++
            if (atEnd()) throw JsonException("bad number at offset $start")
            if (text[pos] == '0') {
                pos++
            } else if (text[pos] in '1'..'9') {
                while (!atEnd() && text[pos] in '0'..'9') pos++
            } else throw JsonException("bad number at offset $start")
            var isDouble = false
            if (!atEnd() && text[pos] == '.') {
                isDouble = true
                pos++
                if (atEnd() || text[pos] !in '0'..'9') throw JsonException("bad fraction at offset $start")
                while (!atEnd() && text[pos] in '0'..'9') pos++
            }
            if (!atEnd() && (text[pos] == 'e' || text[pos] == 'E')) {
                isDouble = true
                pos++
                if (!atEnd() && (text[pos] == '+' || text[pos] == '-')) pos++
                if (atEnd() || text[pos] !in '0'..'9') throw JsonException("bad exponent at offset $start")
                while (!atEnd() && text[pos] in '0'..'9') pos++
            }
            val token = text.substring(start, pos)
            return if (isDouble) {
                token.toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw JsonException("bad number $token")
            } else {
                token.toLongOrNull() ?: throw JsonException("integer out of range: $token")
            }
        }
    }
}
