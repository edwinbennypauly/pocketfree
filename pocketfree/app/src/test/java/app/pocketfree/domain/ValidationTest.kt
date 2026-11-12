package app.pocketfree.domain

import app.pocketfree.domain.TestData.food
import app.pocketfree.domain.TestData.salary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    private val ok = LocalDate.of(2026, 11, 11)
    private fun v(minor: Long = 1000, cur: String = "AED", cat: Category? = food, desc: String = "x", notes: String = "", date: LocalDate = ok, type: TransactionType = TransactionType.EXPENSE, archived: Boolean = false) =
        TransactionValidator.validate(type, minor, cur, cat, desc, notes, date, archived)

    @Test fun validTransactionHasNoErrors() { assertTrue(v().isEmpty()) }
    @Test fun zeroAndNegativeAmountsAreRejected() { assertFalse(v(minor = 0).isEmpty()); assertFalse(v(minor = -5).isEmpty()) }
    @Test fun tooLargeIsRejectedButMaximumIsAllowed() {
        assertTrue(v(minor = TransactionValidator.MAX_MINOR).isEmpty()); assertFalse(v(minor = TransactionValidator.MAX_MINOR + 1).isEmpty())
    }
    @Test fun unknownCurrencyIsRejected() { assertFalse(v(cur = "XYZ").isEmpty()) }
    @Test fun missingOrMismatchedCategoryIsRejected() {
        assertEquals(listOf("Choose a category."), v(cat = null))
        assertFalse(v(cat = salary).isEmpty())
        assertTrue(v(cat = salary, type = TransactionType.INCOME).isEmpty())
    }
    @Test fun archivedCategoryOnlyAllowedWhenEditingOldRecords() {
        val archived = food.copy(isArchived = true)
        assertFalse(v(cat = archived).isEmpty()); assertTrue(v(cat = archived, archived = true).isEmpty())
    }
    @Test fun textLengthAndDateLimits() {
        assertFalse(v(desc = "a".repeat(201)).isEmpty()); assertFalse(v(notes = "a".repeat(2001)).isEmpty())
        assertFalse(v(date = LocalDate.of(1999, 12, 31)).isEmpty()); assertFalse(v(date = LocalDate.of(2101, 1, 1)).isEmpty())
    }
    @Test fun multipleProblemsAreAllReported() { assertEquals(3, v(minor = 0, cur = "ZZZ", cat = null).size) }
}
