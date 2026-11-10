package app.pocketfree.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Supported currencies. All use 2 decimal places. */
enum class AppCurrency(
    val code: String,
    val symbol: String,
    val displayName: String,
    val decimals: Int = 2
) {
    AED("AED", "AED", "UAE Dirham"),
    INR("INR", "₹", "Indian Rupee"),
    USD("USD", "$", "US Dollar"),
    EUR("EUR", "€", "Euro"),
    GBP("GBP", "£", "Pound Sterling"),
    SAR("SAR", "SAR", "Saudi Riyal");

    companion object {
        fun fromCode(code: String?): AppCurrency = entries.firstOrNull { it.code == code } ?: AED
    }
}

/**
 * Money helpers. Amounts are stored as Long minor units (fils/paise/cents) and
 * converted with BigDecimal. Double/Float are never used for money.
 */
object Money {
    private val INPUT_PATTERN = Regex("""^(\d+(\.\d*)?|\.\d+)$""")
    private const val MAX_INPUT_LENGTH = 18

    /** Converts a decimal amount to minor units (HALF_UP). Throws ArithmeticException on overflow. */
    fun toMinor(amount: BigDecimal, decimals: Int = 2): Long =
        amount.setScale(decimals, RoundingMode.HALF_UP).movePointRight(decimals).longValueExact()

    /**
     * Parses user-typed text such as "1,234.50" into minor units.
     * Returns null for empty, negative, non-numeric, scientific-notation or overflowing input.
     */
    fun parseToMinor(input: String, decimals: Int = 2): Long? {
        val cleaned = input.trim().replace(",", "")
        if (cleaned.isEmpty() || cleaned.length > MAX_INPUT_LENGTH) return null
        if (!INPUT_PATTERN.matches(cleaned)) return null
        return try {
            toMinor(BigDecimal(cleaned), decimals)
        } catch (e: ArithmeticException) {
            null
        }
    }

    fun toDecimal(minor: Long, decimals: Int = 2): BigDecimal = BigDecimal.valueOf(minor, decimals)

    /** e.g. 123456 AED -> "AED 1,234.56"; negative -> "-AED 5.00". */
    fun format(minor: Long, currency: AppCurrency): String {
        val formatter = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
        val absolute = toDecimal(minor, currency.decimals).abs()
        val body = "${currency.symbol} ${formatter.format(absolute)}"
        return if (minor < 0) "-$body" else body
    }
}
