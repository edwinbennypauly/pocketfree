package app.pocketfree.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Inclusive date range. */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "Range end is before start" }
    }

    val days: Long get() = ChronoUnit.DAYS.between(start, end) + 1

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    /** Every calendar month touched by this range, oldest first. */
    fun months(): List<YearMonth> {
        val result = ArrayList<YearMonth>()
        var m = YearMonth.from(start)
        val last = YearMonth.from(end)
        while (!m.isAfter(last)) {
            result.add(m)
            m = m.plusMonths(1)
        }
        return result
    }

    companion object {
        fun ofMonth(month: YearMonth) = DateRange(month.atDay(1), month.atEndOfMonth())

        /** The last [count] calendar months including the month of [today]. */
        fun lastMonths(count: Int, today: LocalDate): DateRange {
            val current = YearMonth.from(today)
            return DateRange(current.minusMonths(count - 1L).atDay(1), current.atEndOfMonth())
        }

        fun thisYear(today: LocalDate) = DateRange(LocalDate.of(today.year, 1, 1), LocalDate.of(today.year, 12, 31))

        /** Order-independent constructor for user-picked dates. */
        fun between(a: LocalDate, b: LocalDate) = if (a.isAfter(b)) DateRange(b, a) else DateRange(a, b)
    }
}
