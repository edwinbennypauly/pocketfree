package app.pocketfree.domain.backup

import java.math.BigDecimal

class JsonException(message: String) : Exception(message)

/** Minimal dependency-free JSON reader/writer. Parsed values: Map, List, String, Long, BigDecimal, Boolean, null. */
object Json {
    fun parse(text: String): Any? {
        val parser = Parser(text.removePrefix("\uFEFF"))
        val value = parser.readValue(0)
        parser.skipWhitespace()
        if (!parser.atEnd()) throw JsonException("Unexpected data after the JSON content (position ${parser.pos}).")
        return value
    }

    fun write(value: Any?): String {
        val sb = StringBuilder()
        writeValue(sb, value, 0)
        return sb.toString()
    }

    private fun writeValue(sb: StringBuilder, v: Any?, indent: Int) {
        when (v) {
            null -> sb.append("null")
            is String -> writeString(sb, v)
            is Boolean, is Int, is Long -> sb.append(v.toString())
            is BigDecimal -> sb.append(v.toPlainString())
            is Map<*, *> -> {
                if (v.isEmpty()) { sb.append("{}"); return }
                sb.append("{\n")
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(",\n")
                    first = false
                    pad(sb, indent + 1)
                    writeString(sb, k.toString())
                    sb.append(": ")
                    writeValue(sb, value, indent + 1)
                }
                sb.append("\n")
                pad(sb, indent)
                sb.append("}")
            }
            is List<*> -> {
                if (v.isEmpty()) { sb.append("[]"); return }
                sb.append("[\n")
                for ((i, item) in v.withIndex()) {
                    if (i > 0) sb.append(",\n")
                    pad(sb, indent + 1)
                    writeValue(sb, item, indent + 1)
                }
                sb.append("\n")
                pad(sb, indent)
                sb.append("]")
            }
            else -> throw IllegalArgumentException("Unsupported JSON type: ${v::class.java.name}")
        }
    }

    private fun pad(sb: StringBuilder, level: Int) {
        repeat(level) { sb.append("  ") }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(private val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun skipWhitespace() {
            while (pos < s.length && (s[pos] == ' ' || s[pos] == '\t' || s[pos] == '\r' || s[pos] == '\n')) pos++
        }

        fun readValue(depth: Int): Any? {
            if (depth > 64) throw JsonException("The JSON is nested too deeply.")
            skipWhitespace()
            if (atEnd()) throw JsonException("The file ended unexpectedly.")
            val c = s[pos]
            return when {
                c == '{' -> readObject(depth)
                c == '[' -> readArray(depth)
                c == '"' -> readString()
                c == 't' -> literal("true", true)
                c == 'f' -> literal("false", false)
                c == 'n' -> literal("null", null)
                c == '-' || c in '0'..'9' -> readNumber()
                else -> throw JsonException("Unexpected character '$c' at position $pos.")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, pos)) throw JsonException("Unexpected text at position $pos.")
            pos += word.length
            return value
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            pos++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (pos < s.length && s[pos] == '}') { pos++; return map }
            while (true) {
                skipWhitespace()
                if (atEnd() || s[pos] != '"') throw JsonException("Expected a quoted name at position $pos.")
                val key = readString()
                skipWhitespace()
                if (atEnd() || s[pos] != ':') throw JsonException("Expected ':' at position $pos.")
                pos++
                map[key] = readValue(depth + 1)
                skipWhitespace()
                if (atEnd()) throw JsonException("The file ended unexpectedly.")
                if (s[pos] == ',') { pos++; continue }
                if (s[pos] == '}') { pos++; return map }
                throw JsonException("Expected ',' or '}' at position $pos.")
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            pos++ // [
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (pos < s.length && s[pos] == ']') { pos++; return list }
            while (true) {
                list.add(readValue(depth + 1))
                skipWhitespace()
                if (atEnd()) throw JsonException("The file ended unexpectedly.")
                if (s[pos] == ',') { pos++; continue }
                if (s[pos] == ']') { pos++; return list }
                throw JsonException("Expected ',' or ']' at position $pos.")
            }
        }

        private fun readString(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("A text value is missing its closing quote.")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonException("Bad escape at end of file.")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (pos + 4 > s.length) throw JsonException("Bad unicode escape.")
                                val code = s.substring(pos, pos + 4).toIntOrNull(16)
                                    ?: throw JsonException("Bad unicode escape at position $pos.")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw JsonException("Unknown escape '\\$e' at position ${pos - 1}.")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Any {
            val start = pos
            while (pos < s.length && (s[pos] in '0'..'9' || s[pos] == '-' || s[pos] == '+' || s[pos] == '.' || s[pos] == 'e' || s[pos] == 'E')) pos++
            val token = s.substring(start, pos)
            token.toLongOrNull()?.let { return it }
            return try {
                BigDecimal(token)
            } catch (e: NumberFormatException) {
                throw JsonException("Bad number '$token' at position $start.")
            }
        }
    }
}
