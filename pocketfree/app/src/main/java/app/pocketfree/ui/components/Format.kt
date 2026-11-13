package app.pocketfree.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import app.pocketfree.domain.AppCurrency
import app.pocketfree.domain.Money
import app.pocketfree.domain.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

val LocalHideAmounts = compositionLocalOf { false }

const val HIDDEN_AMOUNT = "••••"

/** Formats money, or masks it when "Hide amounts" is on. */
@Composable
fun moneyText(minor: Long, currencyCode: String): String =
    if (LocalHideAmounts.current) HIDDEN_AMOUNT else Money.format(minor, AppCurrency.fromCode(currencyCode))

/** Short form for small tiles (AED 45.42M). Also masked when "Hide amounts" is on. */
@Composable
fun compactMoneyText(minor: Long, currencyCode: String): String =
    if (LocalHideAmounts.current) HIDDEN_AMOUNT else Money.formatCompact(minor, AppCurrency.fromCode(currencyCode))

val TransactionType.displayName: String
    get() = when (this) {
        TransactionType.EXPENSE -> "Expense"
        TransactionType.INCOME -> "Income"
        TransactionType.INVESTMENT -> "Investment"
        TransactionType.WITHDRAWAL -> "Withdrawal"
    }

/** Short verb for chips. */
val TransactionType.chipLabel: String
    get() = when (this) {
        TransactionType.EXPENSE -> "Expense"
        TransactionType.INCOME -> "Income"
        TransactionType.INVESTMENT -> "Invest"
        TransactionType.WITHDRAWAL -> "Withdraw"
    }

/** Like [moneyText] but with an explicit + or - sign for transaction rows. */
@Composable
fun signedMoneyText(minor: Long, currencyCode: String, negative: Boolean): String {
    val base = moneyText(minor, currencyCode)
    if (LocalHideAmounts.current) return base
    return (if (negative) "-" else "+") + base
}

fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

fun formatMonth(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

fun formatMonthShort(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))

fun dayHeading(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))
}
