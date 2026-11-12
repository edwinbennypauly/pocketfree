package app.pocketfree.domain

import app.pocketfree.domain.TestData.aed
import app.pocketfree.domain.TestData.expense
import app.pocketfree.domain.TestData.food
import app.pocketfree.domain.TestData.rent
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetTest {
    private fun b(ym: String, cat: Long, limit: Long) = Budget(YearMonth.parse(ym), cat, limit, "AED")

    @Test fun utilisationNumbers() {
        val s = BudgetStatus(300_000, 120_000)
        assertEquals(180_000L, s.remainingMinor); assertEquals(40, s.percentUsed); assertEquals(BudgetLevel.OK, s.level)
    }

    @Test fun warningAt80PercentAndOverAbove100() {
        assertEquals(BudgetLevel.OK, BudgetStatus(1000, 799).level)
        assertEquals(BudgetLevel.WARNING, BudgetStatus(1000, 800).level)
        assertEquals(BudgetLevel.WARNING, BudgetStatus(1000, 1000).level)
        assertEquals(BudgetLevel.OVER, BudgetStatus(1000, 1001).level)
        assertEquals(-1L, BudgetStatus(1000, 1001).remainingMinor)
        assertEquals(150, BudgetStatus(1000, 1500).percentUsed)
    }

    @Test fun zeroLimitAndHugeValuesDoNotCrash() {
        assertEquals(0, BudgetStatus(0, 500).percentUsed)
        assertEquals(Int.MAX_VALUE, BudgetStatus(1, Long.MAX_VALUE).percentUsed)
    }

    @Test fun budgetCarriesForwardUntilChanged() {
        val budgets = listOf(b("2026-09", OVERALL_BUDGET_ID, 300_000), b("2026-11", OVERALL_BUDGET_ID, 350_000), b("2026-09", 1, 60_000))
        assertEquals(300_000L, BudgetCalc.effective(budgets, YearMonth.of(2026, 10))[OVERALL_BUDGET_ID]!!.limitMinor)
        assertEquals(350_000L, BudgetCalc.effective(budgets, YearMonth.of(2026, 12))[OVERALL_BUDGET_ID]!!.limitMinor)
        assertEquals(60_000L, BudgetCalc.effective(budgets, YearMonth.of(2026, 12))[1L]!!.limitMinor)
        assertTrue(BudgetCalc.effective(budgets, YearMonth.of(2026, 8)).isEmpty())
    }

    @Test fun zeroLimitClearsABudgetFromThatMonthOn() {
        val budgets = listOf(b("2026-09", 1, 60_000), b("2026-11", 1, 0))
        assertTrue(BudgetCalc.effective(budgets, YearMonth.of(2026, 10)).containsKey(1L))
        assertFalse(BudgetCalc.effective(budgets, YearMonth.of(2026, 11)).containsKey(1L))
    }

    @Test fun spentByCategoryUsesOnlyThatMonthsExpenses() {
        val txs = listOf(expense(100, "2026-11-01", food), expense(50, "2026-11-30", food), expense(999, "2026-12-01", food), expense(70, "2026-11-04", rent))
        val spent = BudgetCalc.spentByCategory(txs, YearMonth.of(2026, 11), aed)
        assertEquals(150L, spent[1L]); assertEquals(70L, spent[2L])
    }
}
