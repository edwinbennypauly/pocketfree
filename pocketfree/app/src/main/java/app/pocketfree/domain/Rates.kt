package app.pocketfree.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Manual exchange rates. A rate for (from, to) means: 1 unit of `from` = rate units of `to`.
 * No rate is ever guessed: if neither the direct nor the inverse rate exists, [convert] returns null.
 */
class RateTable(val base: AppCurrency, rates: Map<Pair<String, String>, BigDecimal>) {
    private val table: Map<Pair<String, String>, BigDecimal> = rates.filterValues { it.signum() > 0 }

    /** The stored (direct) rate, or null. Used for backups. */
    fun rateOrNull(fromCode: String, toCode: String): BigDecimal? = table[fromCode to toCode]

    fun canConvert(fromCode: String): Boolean =
        fromCode == base.code || table.containsKey(fromCode to base.code) || table.containsKey(base.code to fromCode)

    /** Converts minor units of [fromCode] into minor units of the base currency, or null if no rate is set. */
    fun convert(amountMinor: Long, fromCode: String): Long? {
        if (fromCode == base.code) return amountMinor
        val from = AppCurrency.entries.firstOrNull { it.code == fromCode } ?: return null
        val direct = table[fromCode to base.code]
        val factor: BigDecimal = if (direct != null) {
            direct
        } else {
            val inverse = table[base.code to fromCode] ?: return null
            BigDecimal.ONE.divide(inverse, 12, RoundingMode.HALF_UP)
        }
        val shifted = BigDecimal.valueOf(amountMinor).multiply(factor).movePointRight(base.decimals - from.decimals)
        return try {
            shifted.setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    companion object {
        fun parseRate(text: String): BigDecimal? {
            val cleaned = text.trim()
            if (cleaned.isEmpty() || cleaned.length > 20) return null
            if (!Regex("""^(\d+(\.\d*)?|\.\d+)$""").matches(cleaned)) return null
            val value = BigDecimal(cleaned)
            return if (value.signum() > 0) value else null
        }
    }
}
