package app.pocketfree.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInputTest {
    private fun type(vararg keys: Char, decimals: Int = 2) = keys.fold("") { acc, k -> AmountInput.press(acc, k, decimals) }

    @Test fun buildsNumbers() { assertEquals("12.5", type('1', '2', '.', '5')) }
    @Test fun leadingDotBecomesZeroDot() { assertEquals("0.5", type('.', '5')) }
    @Test fun leadingZerosCollapse() { assertEquals("5", type('0', '0', '5')); assertEquals("0", type('0', '0')) }
    @Test fun onlyOneDecimalPointAndTwoDecimals() { assertEquals("1.25", type('1', '.', '.', '2', '5', '9')) }
    @Test fun backspaceAndEmpty() { assertEquals("1", type('1', '2', '<')); assertEquals("", type('<', '<')) }
    @Test fun integerPartIsCapped() { assertEquals(11, type(*"123456789012345".toCharArray()).length) }
    @Test fun zeroDecimalCurrenciesIgnoreThePoint() { assertEquals("12", type('1', '.', '2', decimals = 0)) }
    @Test fun unknownKeysAreIgnored() { assertEquals("1", type('1', 'x')) }
    @Test fun parsedResultMatchesMoney() { assertEquals(1250L, Money.parseToMinor(type('1', '2', '.', '5'))) }
}
