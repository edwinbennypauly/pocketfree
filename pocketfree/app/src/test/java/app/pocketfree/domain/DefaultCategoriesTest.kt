package app.pocketfree.domain

import app.pocketfree.domain.DefaultCategories
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultCategoriesTest {
    @Test fun hasTheSpecifiedCounts() {
        assertEquals(16, DefaultCategories.expense.size)
        assertEquals(7, DefaultCategories.income.size)
        assertEquals(8, DefaultCategories.investment.size)
        assertEquals(8, DefaultCategories.withdrawal.size)
        assertEquals(39, DefaultCategories.all.size)
    }

    @Test fun incomeHasInvestmentReturnsNotAmbiguousInvestments() {
        val names = DefaultCategories.income.map { it.name }
        assertEquals(true, "Investment returns" in names)
        assertEquals(false, "Investments" in names)
    }

    @Test fun investmentAndWithdrawalSeedsUseTheirOwnTypes() {
        assertEquals(true, DefaultCategories.investment.all { it.type == TransactionType.INVESTMENT })
        assertEquals(true, DefaultCategories.withdrawal.all { it.type == TransactionType.WITHDRAWAL })
        assertEquals(DefaultCategories.investment.map { it.name }, DefaultCategories.withdrawal.map { it.name })
    }

    @Test fun namesAreUniqueWithinEachType() {
        assertEquals(DefaultCategories.expense.size, DefaultCategories.expense.map { it.name }.toSet().size)
        assertEquals(DefaultCategories.income.size, DefaultCategories.income.map { it.name }.toSet().size)
    }
}
