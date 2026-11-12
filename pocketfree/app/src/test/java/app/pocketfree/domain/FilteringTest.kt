package app.pocketfree.domain

import app.pocketfree.domain.TestData.categoryMap
import app.pocketfree.domain.TestData.expense
import app.pocketfree.domain.TestData.food
import app.pocketfree.domain.TestData.income
import app.pocketfree.domain.TestData.rent
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilteringTest {
    private val a = expense(1_250, "2026-11-10", food, "Lunch at Zaatar", id = 1)
    private val b = expense(300_000, "2026-11-01", rent, "November rent", id = 2)
    private val c = income(1_000_000, "2026-10-28", description = "Salary", id = 3)
    private val d = expense(1_250, "2026-11-10", food, "Coffee", id = 4)
    private val e = expense(500, "2025-12-31", food, "Old snack", id = 5)
    private val all = listOf(a, b, c, d, e)
    private fun run(f: TransactionFilter) = Filtering.apply(all, f, categoryMap).map { it.id }

    @Test fun defaultIsNewestFirstWithStableTieBreak() { assertEquals(listOf(4L, 1L, 2L, 3L, 5L), run(TransactionFilter())) }
    @Test fun oldestFirst() { assertEquals(listOf(5L, 3L, 2L, 1L, 4L), run(TransactionFilter(sort = SortOrder.OLDEST))) }
    @Test fun highestAndLowestAmount() {
        assertEquals(listOf(3L, 2L, 4L, 1L, 5L), run(TransactionFilter(sort = SortOrder.HIGHEST)))
        assertEquals(listOf(5L, 4L, 1L, 2L, 3L), run(TransactionFilter(sort = SortOrder.LOWEST)))
    }
    @Test fun byType() { assertEquals(listOf(3L), run(TransactionFilter(type = TransactionType.INCOME))) }
    @Test fun byCategory() { assertEquals(listOf(4L, 1L, 5L), run(TransactionFilter(categoryId = food.id))) }
    @Test fun byYearAndMonth() {
        assertEquals(listOf(5L), run(TransactionFilter(year = 2025)))
        assertEquals(listOf(4L, 1L, 2L), run(TransactionFilter(year = 2026, month = 11)))
        assertEquals(listOf(4L, 1L, 2L), run(TransactionFilter(month = 11)))
    }
    @Test fun byDateRangeIsInclusive() {
        assertEquals(listOf(4L, 1L, 2L), run(TransactionFilter(from = LocalDate.of(2026, 11, 1), to = LocalDate.of(2026, 11, 10))))
        assertEquals(listOf(2L), run(TransactionFilter(from = LocalDate.of(2026, 11, 1), to = LocalDate.of(2026, 11, 9))))
    }
    @Test fun searchMatchesDescriptionCategoryAndAmount() {
        assertEquals(listOf(1L), run(TransactionFilter(query = "zaatar")))
        assertEquals(listOf(4L, 1L, 5L), run(TransactionFilter(query = "FOOD")))
        assertEquals(listOf(4L, 1L), run(TransactionFilter(query = "12.50")))
        assertEquals(emptyList<Long>(), run(TransactionFilter(query = "nothing like this")))
    }
    @Test fun filtersCombine() { assertEquals(listOf(4L, 1L), run(TransactionFilter(type = TransactionType.EXPENSE, categoryId = food.id, year = 2026))) }
    @Test fun groupingKeepsOrder() {
        val g = Filtering.groupByDate(Filtering.apply(all, TransactionFilter(), categoryMap))
        assertEquals(LocalDate.of(2026, 11, 10), g[0].first); assertEquals(2, g[0].second.size)
        assertEquals(4, g.size)
    }
    @Test fun hasFiltersIgnoresSort() {
        assertFalse(TransactionFilter(sort = SortOrder.OLDEST).hasFilters); assertTrue(TransactionFilter(query = "x").hasFilters)
    }
    @Test fun amountSortUsesConvertedValueWhenRatesGiven() {
        val usd = expense(2_000, "2026-11-01", food, currency = "USD", id = 10)   // 20 USD = 73.45 AED
        val aedTx = expense(5_000, "2026-11-01", food, id = 11)                    // 50 AED
        val rates = TestData.rates(AppCurrency.AED, Triple("USD", "AED", "3.6725"))
        val sorted = Filtering.apply(listOf(aedTx, usd), TransactionFilter(sort = SortOrder.HIGHEST), categoryMap, rates).map { it.id }
        assertEquals(listOf(10L, 11L), sorted)
    }
}
