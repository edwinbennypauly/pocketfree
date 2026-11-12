package app.pocketfree.domain

import app.pocketfree.domain.TestData.aed
import app.pocketfree.domain.TestData.expense
import app.pocketfree.domain.TestData.food
import app.pocketfree.domain.TestData.income
import app.pocketfree.domain.TestData.rent
import app.pocketfree.domain.TestData.salary
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculationsTest {
    private val nov = DateRange.ofMonth(YearMonth.of(2026, 11))

    @Test fun emptyHistoryIsAllZeros() {
        val s = Calculations.summarize(emptyList(), null, aed)
        assertEquals(0L, s.incomeMinor); assertEquals(0L, s.expenseMinor); assertEquals(0L, s.netMinor)
        assertTrue(Calculations.categoryTotals(emptyList(), nov, TransactionType.EXPENSE, aed).isEmpty())
        val stats = Calculations.stats(emptyList(), nov, aed, LocalDate.of(2026, 11, 11))
        assertEquals(0L, stats.averageDailyMinor); assertNull(stats.highestExpense); assertNull(stats.topCategory)
    }

    @Test fun totalBalanceCoversAllTimeAndMonthCoversOnlyThatMonth() {
        val txs = listOf(
            income(1_000_000, "2026-10-28"), expense(250_000, "2026-10-30"),
            income(1_000_000, "2026-11-01"), expense(120_050, "2026-11-05"), expense(30_000, "2026-11-10", rent)
        )
        val all = Calculations.summarize(txs, null, aed)
        assertEquals(2_000_000L, all.incomeMinor); assertEquals(400_050L, all.expenseMinor); assertEquals(1_599_950L, all.netMinor)
        val m = Calculations.summarize(txs, nov, aed)
        assertEquals(1_000_000L, m.incomeMinor); assertEquals(150_050L, m.expenseMinor); assertEquals(849_950L, m.netMinor)
    }

    @Test fun zeroValueTransactionsChangeNothing() {
        val s = Calculations.summarize(listOf(expense(0, "2026-11-02"), income(0, "2026-11-02")), nov, aed)
        assertEquals(0L, s.netMinor)
    }

    @Test fun largeAmountsStayExact() {
        val big = TransactionValidator.MAX_MINOR
        val s = Calculations.summarize(listOf(income(big, "2026-11-01"), income(big, "2026-11-02"), expense(1, "2026-11-03")), nov, aed)
        assertEquals(2 * big, s.incomeMinor); assertEquals(2 * big - 1, s.netMinor)
    }

    @Test fun monthBoundariesAreInclusive() {
        val txs = listOf(expense(100, "2026-10-31"), expense(200, "2026-11-01"), expense(400, "2026-11-30"), expense(800, "2026-12-01"))
        assertEquals(600L, Calculations.summarize(txs, nov, aed).expenseMinor)
    }

    @Test fun leapYearFebruaryHas29Days() {
        val feb28 = DateRange.ofMonth(YearMonth.of(2028, 2))
        assertEquals(29L, feb28.days)
        assertEquals(28L, DateRange.ofMonth(YearMonth.of(2027, 2)).days)
        val txs = listOf(expense(100, "2028-02-29"), expense(100, "2028-03-01"))
        assertEquals(100L, Calculations.summarize(txs, feb28, aed).expenseMinor)
    }

    @Test fun categorySpendingIsSortedAndKeepsDeletedCategories() {
        val txs = listOf(expense(100, "2026-11-01", food), expense(500, "2026-11-02", rent), expense(50, "2026-11-03", food),
            expense(70, "2026-11-04", Category(99, "Gone", TransactionType.EXPENSE, "x", 0)))
        val totals = Calculations.categoryTotals(txs, nov, TransactionType.EXPENSE, aed)
        assertEquals(listOf(2L, 1L, 99L), totals.map { it.categoryId })
        assertEquals(listOf(500L, 150L, 70L), totals.map { it.totalMinor })
    }

    @Test fun statsAverageHighestAndTopCategory() {
        val txs = listOf(expense(10_000, "2026-11-01", food), expense(50_000, "2026-11-03", rent), expense(1_000, "2026-11-10", food))
        val stats = Calculations.stats(txs, nov, aed, LocalDate.of(2026, 11, 10))
        assertEquals(6_100L, stats.averageDailyMinor)          // 61,000 / 10 days elapsed
        assertEquals(50_000L, stats.highestExpenseMinor)
        assertEquals(2L, stats.topCategory!!.categoryId)
        val finished = Calculations.stats(txs, nov, aed, LocalDate.of(2027, 1, 1))
        assertEquals(2_033L, finished.averageDailyMinor)       // 61,000 / 30 days, rounded half up
    }

    @Test fun dailyExpensesAreZeroFilled() {
        val daily = Calculations.dailyExpenses(listOf(expense(500, "2026-11-02"), expense(250, "2026-11-02")), nov, aed)
        assertEquals(30, daily.size); assertEquals(750L, daily[1].second); assertEquals(0L, daily[0].second)
    }

    @Test fun monthlyTotalsAndMonthList() {
        val range = DateRange(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 11, 2))
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), range.months())
        val txs = listOf(income(900, "2026-09-20"), expense(300, "2026-10-01"))
        val totals = Calculations.monthlyTotals(txs, range.months(), aed)
        assertEquals(900L, totals[0].incomeMinor); assertEquals(300L, totals[1].expenseMinor); assertEquals(0L, totals[2].expenseMinor)
    }

    @Test fun lastThreeMonthsAndYearRanges() {
        val today = LocalDate.of(2026, 11, 11)
        assertEquals(LocalDate.of(2026, 9, 1), DateRange.lastMonths(3, today).start)
        assertEquals(LocalDate.of(2026, 11, 30), DateRange.lastMonths(3, today).end)
        assertEquals(LocalDate.of(2026, 6, 1), DateRange.lastMonths(6, today).start)
        assertEquals(LocalDate.of(2026, 1, 1), DateRange.thisYear(today).start)
        assertEquals(LocalDate.of(2025, 12, 1), DateRange.lastMonths(12, LocalDate.of(2026, 11, 1)).start)
        assertEquals(DateRange.between(today, today.minusDays(3)).start, today.minusDays(3))
    }

    @Test fun monthComparisonMatchesTheSpecExample() {
        val txs = listOf(expense(250_000, "2026-11-10"), expense(210_000, "2026-12-10"))
        val c = Calculations.compareExpenses(txs, YearMonth.of(2026, 11), YearMonth.of(2026, 12), aed)
        assertEquals(-40_000L, c.differenceMinor)
        assertEquals("-16.0", c.percentChange().toString())
    }

    @Test fun comparisonFromZeroHasNoPercentAndGrowthIsPositive() {
        assertNull(Comparison(0, 500).percentChange())
        assertEquals("50.0", Comparison(1000, 1500).percentChange().toString())
        assertEquals("0.0", Comparison(1000, 1000).percentChange().toString())
    }

    @Test fun categoryComparisonOrdersByBiggestChange() {
        val txs = listOf(expense(100, "2026-10-05", food), expense(900, "2026-11-05", food), expense(400, "2026-10-05", rent), expense(300, "2026-11-05", rent))
        val c = Calculations.compareCategories(txs, YearMonth.of(2026, 10), YearMonth.of(2026, 11), aed)
        assertEquals(1L, c[0].categoryId); assertEquals(800L, c[0].differenceMinor); assertEquals(-100L, c[1].differenceMinor)
    }

    // ---- currencies ----
    @Test fun directRateConvertsAndMissingRateIsSkippedNotGuessed() {
        val rates = TestData.rates(AppCurrency.AED, Triple("USD", "AED", "3.6725"))
        val txs = listOf(expense(10_000, "2026-11-02", food, currency = "USD"), expense(5_000, "2026-11-02", food, currency = "EUR"), expense(1_000, "2026-11-02"))
        val s = Calculations.summarize(txs, nov, rates)
        assertEquals(36_725L + 1_000L, s.expenseMinor)
        assertEquals(1, s.skipped)
    }

    @Test fun inverseRateIsUsedWhenOnlyTheOppositeDirectionIsSet() {
        val rates = TestData.rates(AppCurrency.AED, Triple("AED", "INR", "22.5"))
        assertEquals(4_000L, rates.convert(90_000, "INR"))   // 900 INR / 22.5 = 40 AED
        assertNull(rates.convert(100, "EUR"))
        assertEquals(123L, rates.convert(123, "AED"))
    }

    @Test fun rateParsingRejectsBadInput() {
        assertEquals("3.6725", RateTable.parseRate("3.6725").toString())
        assertNull(RateTable.parseRate("0")); assertNull(RateTable.parseRate("-1")); assertNull(RateTable.parseRate("abc")); assertNull(RateTable.parseRate("1e5"))
    }

    @Test fun otherCurrencyAsBaseWorks() {
        val rates = TestData.rates(AppCurrency.USD, Triple("AED", "USD", "0.2723"))
        assertEquals(2_723L, rates.convert(10_000, "AED"))
    }
}
