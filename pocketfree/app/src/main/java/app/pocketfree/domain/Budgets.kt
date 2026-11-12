package app.pocketfree.domain

import java.math.BigInteger
import java.time.YearMonth

enum class BudgetLevel { OK, WARNING, OVER }

data class BudgetStatus(val limitMinor: Long, val spentMinor: Long) {
    val remainingMinor: Long get() = limitMinor - spentMinor

    /** Whole percent of the limit used (rounded down). Not capped at 100. */
    val percentUsed: Int
        get() {
            if (limitMinor <= 0) return 0
            val pct = BigInteger.valueOf(spentMinor).multiply(BigInteger.valueOf(100))
                .divide(BigInteger.valueOf(limitMinor))
            return if (pct > BigInteger.valueOf(Int.MAX_VALUE.toLong())) Int.MAX_VALUE else pct.toInt()
        }

    val level: BudgetLevel
        get() = when {
            spentMinor > limitMinor -> BudgetLevel.OVER
            percentUsed >= WARNING_PERCENT -> BudgetLevel.WARNING
            else -> BudgetLevel.OK
        }

    companion object {
        const val WARNING_PERCENT = 80
    }
}

object BudgetCalc {
    /**
     * The budget in force for each category (OVERALL_BUDGET_ID for the overall one) in [month]:
     * the most recent setting made in or before that month. A limit of 0 means "no budget".
     */
    fun effective(budgets: List<Budget>, month: YearMonth): Map<Long, Budget> =
        budgets.filter { !it.yearMonth.isAfter(month) }
            .groupBy { it.categoryId }
            .mapValues { entry -> entry.value.maxByOrNull { it.yearMonth }!! }
            .filterValues { it.limitMinor > 0 }

    /** Expense spent per category id in [month]. */
    fun spentByCategory(txs: List<Transaction>, month: YearMonth, rates: RateTable): Map<Long, Long> =
        Calculations.categoryTotals(txs, DateRange.ofMonth(month), TransactionType.EXPENSE, rates)
            .associate { it.categoryId to it.totalMinor }
}
