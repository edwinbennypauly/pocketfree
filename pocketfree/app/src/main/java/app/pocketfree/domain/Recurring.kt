package app.pocketfree.domain

import java.time.LocalDate

object Recurring {
    const val MAX_PER_RUN = 1000
    private const val MAX_STEPS = 20_000

    /** The n-th occurrence counted from the start date (so month-end dates never drift: Jan 31, Feb 28/29, Mar 31). */
    fun occurrence(start: LocalDate, frequency: Frequency, n: Long): LocalDate = when (frequency) {
        Frequency.DAILY -> start.plusDays(n)
        Frequency.WEEKLY -> start.plusWeeks(n)
        Frequency.MONTHLY -> start.plusMonths(n)
        Frequency.YEARLY -> start.plusYears(n)
    }

    /** Occurrences that are due on or before [today] and have not been generated yet. */
    fun dueDates(rule: RecurringRule, today: LocalDate): List<LocalDate> {
        if (!rule.isActive) return emptyList()
        val endDate = rule.endDate
        val limit = if (endDate != null && endDate.isBefore(today)) endDate else today
        val last = rule.lastGeneratedDate
        val result = ArrayList<LocalDate>()
        var n = 0L
        while (n < MAX_STEPS && result.size < MAX_PER_RUN) {
            val d = occurrence(rule.startDate, rule.frequency, n)
            if (d.isAfter(limit)) break
            if (last == null || d.isAfter(last)) result.add(d)
            n++
        }
        return result
    }

    /** The next date that will be generated, or null if the rule has ended. */
    fun nextDate(rule: RecurringRule): LocalDate? {
        val endDate = rule.endDate
        val last = rule.lastGeneratedDate
        var n = 0L
        while (n < MAX_STEPS) {
            val d = occurrence(rule.startDate, rule.frequency, n)
            if (endDate != null && d.isAfter(endDate)) return null
            if (last == null || d.isAfter(last)) return d
            n++
        }
        return null
    }
}
