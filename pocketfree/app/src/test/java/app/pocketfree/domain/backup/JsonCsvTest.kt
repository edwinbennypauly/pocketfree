package app.pocketfree.domain.backup

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JsonCsvTest {
    @Test fun jsonRoundTripKeepsEverything() {
        val value = linkedMapOf<String, Any?>(
            "text" to "He said \"hi\"\nline2\ttab \\ slash é ✓ 😀 \u0001",
            "n" to 12345678901L, "neg" to -5L, "d" to BigDecimal("1.50"), "t" to true, "f" to false, "nil" to null,
            "list" to listOf(1L, "two", listOf<Any?>(), emptyMap<String, Any?>()), "nested" to mapOf("a" to 1L)
        )
        val parsed = Json.parse(Json.write(value)) as Map<*, *>
        assertEquals(value["text"], parsed["text"]); assertEquals(12345678901L, parsed["n"]); assertEquals(-5L, parsed["neg"])
        assertEquals(BigDecimal("1.50"), parsed["d"]); assertEquals(true, parsed["t"]); assertEquals(null, parsed["nil"])
        assertEquals(4, (parsed["list"] as List<*>).size)
    }

    @Test fun jsonRejectsBrokenInput() {
        for (bad in listOf("", "{", "[1,", "{\"a\" 1}", "{\"a\":}", "tru", "\"abc", "[1] x", "{\"a\":1,}", "nul", "[\"\\q\"]", "-"))
            try { Json.parse(bad); fail("should reject: $bad") } catch (e: JsonException) { assertTrue(e.message!!.isNotBlank()) }
    }

    @Test fun jsonHandlesBomAndUnicodeEscapes() {
        assertEquals("é", (Json.parse("\uFEFF{\"k\":\"\\u00e9\"}") as Map<*, *>)["k"])
    }

    @Test fun jsonDeepNestingIsRefusedNotCrashed() {
        try { Json.parse("[".repeat(500)); fail() } catch (e: JsonException) { }
    }

    @Test fun csvQuotesCommasQuotesAndNewlines() {
        val rows = listOf(listOf("a", "b,c", "say \"x\"", "line1\nline2", "é"), listOf("", "x", "", "", ""))
        assertEquals(rows, Csv.parse(Csv.write(rows)))
    }

    @Test fun csvAcceptsLfCrlfBomAndMissingFinalNewline() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.parse("\uFEFFa,b\r\n1,2"))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.parse("a,b\n1,2\n\n"))
    }

    @Test fun csvRejectsUnterminatedQuote() {
        try { Csv.parse("a,\"oops\n1,2"); fail() } catch (e: CsvException) { assertTrue(e.message!!.contains("quote")) }
    }
}
