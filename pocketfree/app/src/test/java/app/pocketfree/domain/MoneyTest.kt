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

    @Test fun whatTheKeypadCanProduceIsAlwaysParsable() {
        assertEquals(1200L, Money.parseToMinor("12."))
        assertEquals(0L, Money.parseToMinor("0."))
        assertEquals(1210L, Money.parseToMinor("12.1"))
        assertEquals(9_999_999_999_999L, Money.parseToMinor("99999999999.99"))
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

    @Test fun compactFormatLeavesNormalAmountsAlone() {
        assertEquals("AED 410.73", Money.formatCompact(41_073, AppCurrency.AED))
        assertEquals("AED 99,999.99", Money.formatCompact(9_999_999, AppCurrency.AED))
        assertEquals("AED 0.00", Money.formatCompact(0, AppCurrency.AED))
    }

    @Test fun compactFormatShortensBigAmountsForSmallTiles() {
        assertEquals("AED 100K", Money.formatCompact(10_000_000, AppCurrency.AED))
        assertEquals("AED 150.5K", Money.formatCompact(15_050_000, AppCurrency.AED))
        assertEquals("AED 45.42M", Money.formatCompact(4_541_584_400, AppCurrency.AED))   // 45,415,844.00
        assertEquals("AED 1M", Money.formatCompact(100_000_000, AppCurrency.AED))
        assertEquals("AED 100B", Money.formatCompact(TransactionValidator.MAX_MINOR, AppCurrency.AED))
        assertEquals("-AED 2.5M", Money.formatCompact(-250_000_000, AppCurrency.AED))
        assertEquals("₹ 1.23M", Money.formatCompact(123_400_000, AppCurrency.INR))   // 1,234,000.00
    }

    @Test fun compactFormatPromotesInsteadOfShowing1000K() {
        assertEquals("AED 1M", Money.formatCompact(99_999_999, AppCurrency.AED))   // 999,999.99 rounds up to 1M, not "1000K"
    }
}
