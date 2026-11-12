package app.pocketfree.domain

/** Pure logic behind the on-screen numeric keypad. Keys: '0'..'9', '.', '<' (backspace). */
object AmountInput {
    const val MAX_INTEGER_DIGITS = 11

    fun press(current: String, key: Char, decimals: Int = 2): String = when (key) {
        '<' -> current.dropLast(1)
        '.' -> when {
            decimals == 0 -> current
            current.contains('.') -> current
            current.isEmpty() -> "0."
            else -> "$current."
        }
        in '0'..'9' -> {
            val dot = current.indexOf('.')
            if (dot >= 0) {
                if (current.length - dot - 1 >= decimals) current else current + key
            } else when {
                current == "0" -> if (key == '0') current else key.toString()
                current.length >= MAX_INTEGER_DIGITS -> current
                else -> current + key
            }
        }
        else -> current
    }
}
