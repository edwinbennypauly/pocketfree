package app.pocketfree.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringTest {
    private fun rule(start: String, f: Frequency, last: String? = null, end: String? = null, active: Boolean = true) = RecurringRule(
        1, TransactionType.EXPENSE, 100, "AED", 1, "Rent", "", f, LocalDate.parse(start), end?.let { LocalDate.parse(it) }, last?.let { LocalDate.parse(it) }, active
    )
    private fun due(r: RecurringRule, today: String) = Recurring.dueDates(r, LocalDate.parse(today)).map { it.toString() }

    @Test fun monthlyCatchesUpOnceEach() { assertEquals(listOf("2026-09-05", "2026-10-05", "2026-11-05"), due(rule("2026-09-05", Frequency.MONTHLY), "2026-11-11")) }
    @Test fun nothingDueBeforeStart() { assertTrue(due(rule("2026-12-01", Frequency.MONTHLY), "2026-11-11").isEmpty()) }
    @Test fun runningTwiceNeverDuplicates() {
        val first = rule("2026-09-05", Frequency.MONTHLY)
        val last = Recurring.dueDates(first, LocalDate.of(2026, 11, 11)).last()
        assertTrue(Recurring.dueDates(first.copy(lastGeneratedDate = last), LocalDate.of(2026, 11, 20)).isEmpty())
        assertEquals(listOf("2026-12-05"), due(first.copy(lastGeneratedDate = last), "2026-12-06"))
    }
    @Test fun monthEndDatesDoNotDrift() {
        assertEquals(listOf("2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30"), due(rule("2027-01-31", Frequency.MONTHLY), "2027-04-30"))
    }
    @Test fun leapYearFebruary() {
        assertEquals(listOf("2028-01-31", "2028-02-29", "2028-03-31"), due(rule("2028-01-31", Frequency.MONTHLY), "2028-03-31"))
    }
    @Test fun yearlyOnLeapDayFallsBackToFeb28() { assertEquals(listOf("2028-02-29", "2029-02-28", "2030-02-28"), due(rule("2028-02-29", Frequency.YEARLY), "2030-06-01")) }
    @Test fun weeklyAndDaily() {
        assertEquals(listOf("2026-11-01", "2026-11-08"), due(rule("2026-11-01", Frequency.WEEKLY), "2026-11-14"))
        assertEquals(3, due(rule("2026-11-09", Frequency.DAILY), "2026-11-11").size)
    }
    @Test fun endDateStopsGeneration() { assertEquals(listOf("2026-09-05", "2026-10-05"), due(rule("2026-09-05", Frequency.MONTHLY, end = "2026-10-20"), "2026-12-01")) }
    @Test fun inactiveRulesGenerateNothing() { assertTrue(due(rule("2026-09-05", Frequency.MONTHLY, active = false), "2026-12-01").isEmpty()) }
    @Test fun catchUpIsCapped() { assertEquals(Recurring.MAX_PER_RUN, due(rule("2015-01-01", Frequency.DAILY), "2026-11-11").size) }
    @Test fun nextDate() {
        assertEquals(LocalDate.of(2026, 12, 5), Recurring.nextDate(rule("2026-09-05", Frequency.MONTHLY, last = "2026-11-05")))
        assertNull(Recurring.nextDate(rule("2026-09-05", Frequency.MONTHLY, last = "2026-10-05", end = "2026-10-31")))
    }
}
