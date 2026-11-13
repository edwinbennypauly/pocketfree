package app.pocketfree.domain

import app.pocketfree.domain.TestData.aed
import app.pocketfree.domain.TestData.expense
import app.pocketfree.domain.TestData.food
import app.pocketfree.domain.TestData.income
import app.pocketfree.domain.TestData.invest
import app.pocketfree.domain.TestData.returns
import app.pocketfree.domain.TestData.stocks
import app.pocketfree.domain.TestData.stocksOut
import app.pocketfree.domain.TestData.withdraw
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Investments are transfers: neither income nor expense. These tests protect that rule. */
class InvestmentTest {
    private val nov = DateRange.ofMonth(YearMonth.of(2026, 11))

    private val txs = listOf(
        income(1_000_000, "2026-11-01"),                     // salary 10,000
        expense(200_000, "2026-11-05"),                      // spending 2,000
        invest(300_000, "2026-11-10"),                       // put 3,000 into stocks
        withdraw(50_000, "2026-11-20"),                      // took 500 back out
        income(20_000, "2026-11-25", returns, "Dividend")    // 200 profit = real income
    )

    @Test fun investmentsNeverCountAsIncomeOrExpense() {
        val s = Calculations.summarize(txs, nov, aed)
        assertEquals(1_020_000L, s.incomeMinor)      // salary + dividend only
        assertEquals(200_000L, s.expenseMinor)       // spending only
        assertEquals(300_000L, s.investedMinor)
        assertEquals(50_000L, s.withdrawnMinor)
    }

    @Test fun savingsIsIncomeMinusExpensesAndIgnoresInvesting() {
        val s = Calculations.summarize(txs, nov, aed)
        assertEquals(820_000L, s.netMinor)
        assertEquals(250_000L, s.netInvestedMinor)
    }

    @Test fun totalBalanceIsCashAfterInvesting() {
        val s = Calculations.summarize(txs, null, aed)
        assertEquals(570_000L, s.balanceMinor)       // 10,200 income - 2,000 expenses - 2,500 net invested = 5,700
    }

    @Test fun movingMoneyIntoAnInvestmentLowersCashButNotSavings() {
        val before = Calculations.summarize(listOf(income(500_000, "2026-11-01")), null, aed)
        val after = Calculations.summarize(listOf(income(500_000, "2026-11-01"), invest(100_000, "2026-11-02")), null, aed)
        assertEquals(500_000L, before.balanceMinor); assertEquals(400_000L, after.balanceMinor)
        assertEquals(before.netMinor, after.netMinor)
        assertEquals(before.expenseMinor, after.expenseMinor)
    }

    @Test fun withdrawingPutsCashBackWithoutCreatingIncome() {
        val s = Calculations.summarize(listOf(income(500_000, "2026-11-01"), invest(200_000, "2026-11-02"), withdraw(200_000, "2026-11-20")), null, aed)
        assertEquals(500_000L, s.balanceMinor)       // fully round-tripped
        assertEquals(500_000L, s.incomeMinor)        // withdrawal is not income
        assertEquals(0L, s.netInvestedMinor)
    }

    @Test fun balanceUpToFollowsMonthsAndIncludesInvesting() {
        val data = listOf(income(1_000_000, "2026-10-05"), invest(400_000, "2026-10-20"), expense(100_000, "2026-11-03"), withdraw(150_000, "2026-11-15"))
        assertEquals(600_000L, Calculations.balanceUpTo(data, LocalDate.of(2026, 10, 31), aed).balanceMinor)
        assertEquals(650_000L, Calculations.balanceUpTo(data, LocalDate.of(2026, 11, 30), aed).balanceMinor)
        assertEquals(400_000L, Calculations.balanceUpTo(data, LocalDate.of(2026, 10, 31), aed).netInvestedMinor)
    }

    @Test fun investmentsStayOutOfExpenseReports() {
        val totals = Calculations.categoryTotals(txs, nov, TransactionType.EXPENSE, aed)
        assertEquals(1, totals.size); assertEquals(food.id, totals[0].categoryId)
        val stats = Calculations.stats(txs, nov, aed, LocalDate.of(2026, 11, 30))
        assertEquals(200_000L, stats.highestExpenseMinor)     // not the 3,000 investment
        assertEquals(6_667L, stats.averageDailyMinor)         // 2,000 / 30 days
        assertEquals(200_000L, Calculations.dailyExpenses(txs, nov, aed).sumOf { it.second })
        assertEquals(200_000L, BudgetCalc.spentByCategory(txs, YearMonth.of(2026, 11), aed)[food.id])
    }

    @Test fun investmentsDoNotBreakBudgetsOrComparisons() {
        val c = Calculations.compareExpenses(txs, YearMonth.of(2026, 10), YearMonth.of(2026, 11), aed)
        assertEquals(0L, c.fromMinor); assertEquals(200_000L, c.toMinor)
        assertEquals(1, BudgetCalc.spentByCategory(txs, YearMonth.of(2026, 11), aed).size)
    }

    @Test fun savingsRate() {
        assertEquals("80.0", Summary(1_000_000, 200_000, 0).savingsRatePercent().toString())
        assertEquals("-50.0", Summary(1_000, 1_500, 0).savingsRatePercent().toString())
        assertEquals("0.0", Summary(1_000, 1_000, 0).savingsRatePercent().toString())
        assertNull(Summary(0, 500, 0).savingsRatePercent())
        assertEquals("33.3", Summary(300, 200, 0).savingsRatePercent().toString())
    }

    @Test fun otherCurrenciesAreConvertedForInvestmentsToo() {
        val rates = TestData.rates(AppCurrency.AED, Triple("USD", "AED", "3.6725"))
        val s = Calculations.summarize(listOf(TestData.tx(TransactionType.INVESTMENT, 10_000, "2026-11-02", stocks, currency = "USD")), nov, rates)
        assertEquals(36_725L, s.investedMinor)
        val skipped = Calculations.summarize(listOf(TestData.tx(TransactionType.INVESTMENT, 10_000, "2026-11-02", stocks, currency = "EUR")), nov, rates)
        assertEquals(1, skipped.skipped); assertEquals(0L, skipped.investedMinor)
    }

    @Test fun typeFilterFindsInvestmentsAndWithdrawals() {
        val cats = (TestData.categories + stocks + stocksOut + returns).associateBy { it.id }
        fun ids(t: TransactionType) = Filtering.apply(txs, TransactionFilter(type = t), cats).size
        assertEquals(1, ids(TransactionType.INVESTMENT)); assertEquals(1, ids(TransactionType.WITHDRAWAL))
        assertEquals(2, ids(TransactionType.INCOME)); assertEquals(1, ids(TransactionType.EXPENSE))
    }

    @Test fun investmentTransactionsMustUseInvestmentCategories() {
        fun errs(type: TransactionType, cat: Category) = TransactionValidator.validate(type, 1000, "AED", cat, "", "", LocalDate.of(2026, 11, 1))
        assertTrue(errs(TransactionType.INVESTMENT, stocks).isEmpty())
        assertTrue(errs(TransactionType.WITHDRAWAL, stocksOut).isEmpty())
        assertFalse(errs(TransactionType.INVESTMENT, food).isEmpty())          // investing into "Food"? no
        assertFalse(errs(TransactionType.INVESTMENT, stocksOut).isEmpty())     // investment into a withdrawal category? no
        assertFalse(errs(TransactionType.EXPENSE, stocks).isEmpty())
    }

    @Test fun savingsRateDefaultDoesNotChangeExistingSummaryUsage() {
        val s = Summary(100, 40, 2)
        assertEquals(0L, s.investedMinor); assertEquals(60L, s.balanceMinor); assertEquals(60L, s.netMinor)
    }
}
