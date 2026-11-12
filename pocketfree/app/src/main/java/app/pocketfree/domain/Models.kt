package app.pocketfree.domain

import java.time.LocalDate
import java.time.YearMonth

enum class TransactionType { EXPENSE, INCOME }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Colour themes. DYNAMIC uses the phone's wallpaper colours on Android 12+ and falls back to TEAL. */
enum class AccentTheme(val label: String) {
    TEAL("Teal"), BLUE("Blue"), PURPLE("Purple"), GREEN("Green"), ORANGE("Orange"), ROSE("Rose"), DYNAMIC("Wallpaper")
}

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/** Budget rows with this category id are the overall monthly budget. */
const val OVERALL_BUDGET_ID = 0L

data class Category(
    val id: Long,
    val name: String,
    val type: TransactionType,
    val iconKey: String,
    val colorArgb: Int,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0
)

data class Transaction(
    val id: Long,
    val type: TransactionType,
    val amountMinor: Long,
    val currencyCode: String,
    val categoryId: Long,
    val description: String,
    val notes: String,
    val date: LocalDate,
    val recurringRuleId: Long? = null
)

/** A budget set for [yearMonth] applies to that month and later months until replaced. limitMinor 0 = cleared. */
data class Budget(
    val yearMonth: YearMonth,
    val categoryId: Long,
    val limitMinor: Long,
    val currencyCode: String
)

data class RecurringRule(
    val id: Long,
    val type: TransactionType,
    val amountMinor: Long,
    val currencyCode: String,
    val categoryId: Long,
    val description: String,
    val notes: String,
    val frequency: Frequency,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val lastGeneratedDate: LocalDate?,
    val isActive: Boolean
)

/** Everything the screens need, loaded from the database. */
data class LedgerSnapshot(
    val categories: List<Category>,
    val transactions: List<Transaction>,
    val budgets: List<Budget>,
    val recurring: List<RecurringRule>,
    val rates: RateTable,
    val defaultCurrency: AppCurrency
) {
    val categoryMap: Map<Long, Category> by lazy { categories.associateBy { it.id } }
}
