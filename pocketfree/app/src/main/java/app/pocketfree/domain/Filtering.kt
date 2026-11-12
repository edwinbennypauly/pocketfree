package app.pocketfree.domain

import java.time.LocalDate

enum class SortOrder { NEWEST, OLDEST, HIGHEST, LOWEST }

data class TransactionFilter(
    val query: String = "",
    val type: TransactionType? = null,
    val categoryId: Long? = null,
    val year: Int? = null,
    val month: Int? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val sort: SortOrder = SortOrder.NEWEST
) {
    val hasFilters: Boolean
        get() = query.isNotBlank() || type != null || categoryId != null || year != null ||
            month != null || from != null || to != null
}

object Filtering {
    fun apply(
        txs: List<Transaction>,
        filter: TransactionFilter,
        categories: Map<Long, Category>,
        rates: RateTable? = null
    ): List<Transaction> {
        val q = filter.query.trim().lowercase()
        val from = filter.from
        val to = filter.to
        val filtered = txs.filter { t ->
            (filter.type == null || t.type == filter.type) &&
                (filter.categoryId == null || t.categoryId == filter.categoryId) &&
                (filter.year == null || t.date.year == filter.year) &&
                (filter.month == null || t.date.monthValue == filter.month) &&
                (from == null || !t.date.isBefore(from)) &&
                (to == null || !t.date.isAfter(to)) &&
                (q.isEmpty() || matches(t, q, categories))
        }
        return when (filter.sort) {
            SortOrder.NEWEST -> filtered.sortedWith(
                compareByDescending<Transaction> { it.date }.thenByDescending { it.id }
            )
            SortOrder.OLDEST -> filtered.sortedWith(
                compareBy<Transaction> { it.date }.thenBy { it.id }
            )
            SortOrder.HIGHEST -> filtered.sortedWith(
                compareByDescending<Transaction> { value(it, rates) }
                    .thenByDescending { it.date }.thenByDescending { it.id }
            )
            SortOrder.LOWEST -> filtered.sortedWith(
                compareBy<Transaction> { value(it, rates) }
                    .thenByDescending { it.date }.thenByDescending { it.id }
            )
        }
    }

    fun groupByDate(sorted: List<Transaction>): List<Pair<LocalDate, List<Transaction>>> =
        sorted.groupBy { it.date }.toList()

    private fun value(t: Transaction, rates: RateTable?): Long =
        rates?.convert(t.amountMinor, t.currencyCode) ?: t.amountMinor

    private fun matches(t: Transaction, q: String, categories: Map<Long, Category>): Boolean {
        if (t.description.lowercase().contains(q)) return true
        if (t.notes.lowercase().contains(q)) return true
        val categoryName = categories[t.categoryId]?.name?.lowercase()
        if (categoryName != null && categoryName.contains(q)) return true
        return Money.toDecimal(t.amountMinor).toPlainString().contains(q)
    }
}
