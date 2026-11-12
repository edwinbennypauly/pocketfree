package app.pocketfree.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test fun correctPinVerifiesWrongPinDoesNot() {
        val salt = PinHasher.newSalt(); val hash = PinHasher.hash("4821", salt); val saltHex = PinHasher.toHex(salt)
        assertTrue(PinHasher.verify("4821", saltHex, hash)); assertFalse(PinHasher.verify("4822", saltHex, hash))
    }
    @Test fun saltsDifferSoHashesDiffer() { assertNotEquals(PinHasher.hash("1234", PinHasher.newSalt()), PinHasher.hash("1234", PinHasher.newSalt())) }
    @Test fun hashDoesNotContainThePin() { assertFalse(PinHasher.hash("1234", PinHasher.newSalt()).contains("1234")) }
    @Test fun pinFormatRules() {
        assertTrue(PinHasher.isValidPin("1234")); assertTrue(PinHasher.isValidPin("123456"))
        assertFalse(PinHasher.isValidPin("123")); assertFalse(PinHasher.isValidPin("1234567")); assertFalse(PinHasher.isValidPin("12a4")); assertFalse(PinHasher.isValidPin(""))
    }
    @Test fun hexRoundTrip() { assertEquals("00ff10", PinHasher.toHex(PinHasher.fromHex("00ff10"))); assertFalse(PinHasher.verify("1", "zz", "abc")) }
}
