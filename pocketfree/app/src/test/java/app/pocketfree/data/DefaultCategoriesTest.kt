package app.pocketfree.data

import app.pocketfree.data.db.DefaultCategories
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultCategoriesTest {
    @Test fun hasTheSpecifiedCounts() {
        assertEquals(16, DefaultCategories.expense.size)
        assertEquals(7, DefaultCategories.income.size)
    }

    @Test fun namesAreUniqueWithinEachType() {
        assertEquals(DefaultCategories.expense.size, DefaultCategories.expense.map { it.name }.toSet().size)
        assertEquals(DefaultCategories.income.size, DefaultCategories.income.map { it.name }.toSet().size)
    }
}
