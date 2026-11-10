package app.pocketfree.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parsesPlainAndGroupedAmounts() {
        assertEquals(1250L, Money.parseToMinor("12.5"))
        assertEquals(123456L, Money.parseToMinor("1,234.56"))
        assertEquals(500L, Money.parseToMinor("5"))
        assertEquals(50L, Money.parseToMinor(".5"))
    }

    @Test fun zeroParsesToZero() {
        assertEquals(0L, Money.parseToMinor("0"))
        assertEquals(0L, Money.parseToMinor("0.00"))
    }

    @Test fun roundsHalfUpBeyondTwoDecimals() {
        assertEquals(1235L, Money.parseToMinor("12.345"))
        assertEquals(1234L, Money.parseToMinor("12.344"))
    }

    @Test fun rejectsInvalidInput() {
        assertNull(Money.parseToMinor(""))
        assertNull(Money.parseToMinor("abc"))
        assertNull(Money.parseToMinor("-5"))
        assertNull(Money.parseToMinor("1e5"))
        assertNull(Money.parseToMinor("1.2.3"))
        assertNull(Money.parseToMinor("1e999999999"))
    }

    @Test fun rejectsOverflow() {
        assertNull(Money.parseToMinor("99999999999999999"))
    }

    @Test fun largeAmountsAreExact() {
        assertEquals(922337203685477500L, Money.parseToMinor("9223372036854775"))
    }

    @Test fun minorUnitSumsHaveNoFloatingPointError() {
        val sum = Money.parseToMinor("0.1")!! + Money.parseToMinor("0.2")!!
        assertEquals(30L, sum)
        assertEquals(BigDecimal("0.30"), Money.toDecimal(sum))
    }

    @Test fun formatsPositiveAndNegative() {
        assertEquals("AED 1,234.56", Money.format(123456, AppCurrency.AED))
        assertEquals("-AED 5.00", Money.format(-500, AppCurrency.AED))
        assertEquals("₹ 0.05", Money.format(5, AppCurrency.INR))
    }

    @Test fun unknownCurrencyCodeFallsBackToAed() {
        assertEquals(AppCurrency.AED, AppCurrency.fromCode("XYZ"))
        assertEquals(AppCurrency.AED, AppCurrency.fromCode(null))
        assertEquals(AppCurrency.SAR, AppCurrency.fromCode("SAR"))
    }
}
