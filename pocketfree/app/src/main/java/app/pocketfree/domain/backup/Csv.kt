package app.pocketfree.domain.backup

class CsvException(message: String) : Exception(message)

/** RFC 4180 style CSV reader/writer (quotes, doubled quotes, commas and line breaks inside quotes). */
object Csv {
    fun write(rows: List<List<String>>): String {
        val sb = StringBuilder()
        for (row in rows) {
            sb.append(row.joinToString(",") { quote(it) })
            sb.append("\r\n")
        }
        return sb.toString()
    }

    private fun quote(field: String): String {
        val needsQuotes = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }

    fun parse(text: String): List<List<String>> {
        val s = text.removePrefix("\uFEFF")
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var fieldStarted = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else {
                when (c) {
                    '"' -> {
                        if (field.isNotEmpty()) throw CsvException("Unexpected quote in the middle of a value.")
                        inQuotes = true
                        fieldStarted = true
                    }
                    ',' -> { row.add(field.toString()); field.setLength(0); fieldStarted = true }
                    '\r' -> { /* handled with \n */ }
                    '\n' -> {
                        row.add(field.toString()); field.setLength(0)
                        rows.add(row); row = ArrayList(); fieldStarted = false
                    }
                    else -> { field.append(c); fieldStarted = true }
                }
            }
            i++
        }
        if (inQuotes) throw CsvException("A quoted value is missing its closing quote.")
        if (fieldStarted || field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows.filter { r -> !(r.size == 1 && r[0].isBlank()) }
    }
}
