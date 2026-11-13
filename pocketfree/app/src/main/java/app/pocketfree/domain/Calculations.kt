package app.pocketfree.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Totals are in the default (base) currency. Transactions with no usable exchange rate are counted in [skipped]. */
data class Summary(
    val incomeMinor: Long,
    val expenseMinor: Long,
    val skipped: Int,
    val investedMinor: Long = 0,
    val withdrawnMinor: Long = 0
) {
    /** Savings: income minus expenses. Investments are not subtracted here (they are what savings get used for). */
    val netMinor: Long get() = incomeMinor - expenseMinor

    /** Money moved into investments minus money taken back out. */
    val netInvestedMinor: Long get() = investedMinor - withdrawnMinor

    /** Cash left: income - expenses - net invested. This is the "Total balance". */
    val balanceMinor: Long get() = netMinor - netInvestedMinor

    /** Share of income kept after expenses, one decimal (can be negative when overspending). Null without income. */
    fun savingsRatePercent(): BigDecimal? {
        if (incomeMinor <= 0) return null
        return BigDecimal.valueOf(netMinor).multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(incomeMinor), 1, RoundingMode.HALF_UP)
    }
}

data class CategoryTotal(val categoryId: Long, val totalMinor: Long)

data class MonthTotal(val month: YearMonth, val incomeMinor: Long, val expenseMinor: Long)

data class PeriodStats(
    val summary: Summary,
    val averageDailyMinor: Long,
    val highestExpense: Transaction?,
    val highestExpenseMinor: Long,
    val topCategory: CategoryTotal?
)

data class Comparison(val fromMinor: Long, val toMinor: Long) {
    val differenceMinor: Long get() = toMinor - fromMinor

    /** Percent change from -> to with one decimal, or null when the starting value is zero. */
    fun percentChange(): BigDecimal? {
        if (fromMinor == 0L) return null
        return BigDecimal.valueOf(differenceMinor).multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(fromMinor), 1, RoundingMode.HALF_UP)
    }
}

data class CategoryComparison(val categoryId: Long, val fromMinor: Long, val toMinor: Long) {
    val differenceMinor: Long get() = toMinor - fromMinor
}

object Calculations {

    /** Income/expense totals for [range] (or for all time when null). Also gives the overall balance when range is null. */
    fun summarize(txs: List<Transaction>, range: DateRange?, rates: RateTable): Summary {
        var income = 0L
        var expense = 0L
        var invested = 0L
        var withdrawn = 0L
        var skipped = 0
        for (t in txs) {
            if (range != null && t.date !in range) continue
            val value = rates.convert(t.amountMinor, t.currencyCode)
            if (value == null) {
                skipped++
                continue
            }
            when (t.type) {
                TransactionType.INCOME -> income += value
                TransactionType.EXPENSE -> expense += value
                TransactionType.INVESTMENT -> invested += value
                TransactionType.WITHDRAWAL -> withdrawn += value
            }
        }
        return Summary(income, expense, skipped, invested, withdrawn)
    }

    /**
     * Running cash balance (income - expenses - net invested) of everything dated on or before [endInclusive].
     * Used by the Home balance card so it follows the selected month.
     */
    fun balanceUpTo(txs: List<Transaction>, endInclusive: LocalDate, rates: RateTable): Summary {
        var income = 0L
        var expense = 0L
        var invested = 0L
        var withdrawn = 0L
        var skipped = 0
        for (t in txs) {
            if (t.date.isAfter(endInclusive)) continue
            val value = rates.convert(t.amountMinor, t.currencyCode)
            if (value == null) {
                skipped++
                continue
            }
            when (t.type) {
                TransactionType.INCOME -> income += value
                TransactionType.EXPENSE -> expense += value
                TransactionType.INVESTMENT -> invested += value
                TransactionType.WITHDRAWAL -> withdrawn += value
            }
        }
        return Summary(income, expense, skipped, invested, withdrawn)
    }

    /** Totals per category id (largest first). Categories that were deleted still appear under their old id. */
    fun categoryTotals(
        txs: List<Transaction>,
        range: DateRange,
        type: TransactionType,
        rates: RateTable
    ): List<CategoryTotal> {
        val totals = LinkedHashMap<Long, Long>()
        for (t in txs) {
            if (t.type != type || t.date !in range) continue
            val value = rates.convert(t.amountMinor, t.currencyCode) ?: continue
            totals[t.categoryId] = (totals[t.categoryId] ?: 0L) + value
        }
        return totals.map { CategoryTotal(it.key, it.value) }
            .sortedWith(compareByDescending<CategoryTotal> { it.totalMinor }.thenBy { it.categoryId })
    }

    /** Expense per day for every day in [range] (zero-filled), oldest first. */
    fun dailyExpenses(txs: List<Transaction>, range: DateRange, rates: RateTable): List<Pair<LocalDate, Long>> {
        val perDay = HashMap<LocalDate, Long>()
        for (t in txs) {
            if (t.type != TransactionType.EXPENSE || t.date !in range) continue
            val value = rates.convert(t.amountMinor, t.currencyCode) ?: continue
            perDay[t.date] = (perDay[t.date] ?: 0L) + value
        }
        val result = ArrayList<Pair<LocalDate, Long>>()
        var d = range.start
        while (!d.isAfter(range.end)) {
            result.add(d to (perDay[d] ?: 0L))
            d = d.plusDays(1)
        }
        return result
    }

    fun monthlyTotals(txs: List<Transaction>, months: List<YearMonth>, rates: RateTable): List<MonthTotal> =
        months.map { m ->
            val s = summarize(txs, DateRange.ofMonth(m), rates)
            MonthTotal(m, s.incomeMinor, s.expenseMinor)
        }

    fun stats(txs: List<Transaction>, range: DateRange, rates: RateTable, today: LocalDate): PeriodStats {
        val summary = summarize(txs, range, rates)
        val lastCounted = if (today.isBefore(range.end)) today else range.end
        val days = if (lastCounted.isBefore(range.start)) range.days
        else ChronoUnit.DAYS.between(range.start, lastCounted) + 1
        val average = BigDecimal.valueOf(summary.expenseMinor)
            .divide(BigDecimal.valueOf(days), 0, RoundingMode.HALF_UP).longValueExact()

        var highest: Transaction? = null
        var highestValue = 0L
        for (t in txs) {
            if (t.type != TransactionType.EXPENSE || t.date !in range) continue
            val value = rates.convert(t.amountMinor, t.currencyCode) ?: continue
            if (highest == null || value > highestValue) {
                highest = t
                highestValue = value
            }
        }
        val top = categoryTotals(txs, range, TransactionType.EXPENSE, rates).firstOrNull()
        return PeriodStats(summary, average, highest, highestValue, top)
    }

    /** Expense total of [from] month vs [to] month. */
    fun compareExpenses(txs: List<Transaction>, from: YearMonth, to: YearMonth, rates: RateTable): Comparison {
        val a = summarize(txs, DateRange.ofMonth(from), rates).expenseMinor
        val b = summarize(txs, DateRange.ofMonth(to), rates).expenseMinor
        return Comparison(a, b)
    }

    /** Per-category expense in two months, biggest absolute change first. */
    fun compareCategories(txs: List<Transaction>, from: YearMonth, to: YearMonth, rates: RateTable): List<CategoryComparison> {
        val a = categoryTotals(txs, DateRange.ofMonth(from), TransactionType.EXPENSE, rates).associate { it.categoryId to it.totalMinor }
        val b = categoryTotals(txs, DateRange.ofMonth(to), TransactionType.EXPENSE, rates).associate { it.categoryId to it.totalMinor }
        return (a.keys + b.keys).map { id -> CategoryComparison(id, a[id] ?: 0L, b[id] ?: 0L) }
            .sortedWith(compareByDescending<CategoryComparison> { kotlin.math.abs(it.differenceMinor) }.thenBy { it.categoryId })
    }
}
