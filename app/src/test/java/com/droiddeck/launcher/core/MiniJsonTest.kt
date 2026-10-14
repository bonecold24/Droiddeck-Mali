package com.droiddeck.launcher.core

import org.junit.Assert.*
import org.junit.Test

class MiniJsonTest {
    @Test fun parsesFlatObject() {
        val v = MiniJson.parse("""{"appId":"620","fps":60,"on":true,"off":false,"nothing":null}""")
        assertTrue(v is Map<*, *>)
        @Suppress("UNCHECKED_CAST") val m = v as Map<String, Any?>
        assertEquals("620", m["appId"])
        assertEquals(60L, m["fps"])
        assertEquals(true, m["on"])
        assertEquals(false, m["off"])
        assertTrue(m.containsKey("nothing"))
        assertNull(m["nothing"])
    }

    @Test fun parsesNestedArraysAndEscapes() {
        val v = MiniJson.parse("""{"a":[1,"x\"y",-2.5],"b":{"c":"d\\e"}}""")
        @Suppress("UNCHECKED_CAST") val m = v as Map<String, Any?>
        @Suppress("UNCHECKED_CAST") val a = m["a"] as List<Any?>
        assertEquals(listOf(1L, "x\"y", -2.5), a)
        @Suppress("UNCHECKED_CAST") val b = m["b"] as Map<String, Any?>
        assertEquals("d\\e", b["c"])
    }

    @Test fun whitespaceAndEmptyContainers() {
        assertEquals(emptyMap<String, Any?>(), MiniJson.parse(" { } "))
        assertEquals(emptyList<Any?>(), MiniJson.parse("\n[\t]\n"))
        assertEquals("x", MiniJson.parse("\"x\""))
    }

    @Test fun corruptInputThrowsJsonException() {
        listOf("", "{", "{\"a\":}", "{\"a\":1,}", "[1,2", "{\"a\" 1}", "{'a':1}", "{\"a\":tru}", "01", "1.", "--1", "{\"a\":NaN}").forEach { bad ->
            try {
                MiniJson.parse(bad)
                fail("expected JsonException for: $bad")
            } catch (e: MiniJson.JsonException) {
                // expected
            }
        }
    }

    @Test fun trailingDataThrows() {
        try {
            MiniJson.parse("{} {}")
            fail("expected JsonException")
        } catch (e: MiniJson.JsonException) {
        }
    }

    @Test fun stringifyRoundTrips() {
        val original = mapOf(
            "appId" to "620",
            "fps" to 120L,
            "ratio" to 1.5,
            "flags" to mapOf("A" to "1"),
            "list" to listOf("x", null, true),
            "quote" to "a\"b\\c\n",
        )
        val text = MiniJson.stringify(original)
        assertEquals(original, MiniJson.parse(text))
    }

    @Test fun stringifyEscapesControlCharacters() {
        val text = MiniJson.stringify(mapOf("k" to "tab\there"))
        assertTrue(text.contains("\\t"))
        assertEquals("tab\there", (MiniJson.parse(text) as Map<*, *>)["k"])
    }

    @Test fun numbersKeepIntegralType() {
        assertEquals(7L, MiniJson.parse("7"))
        assertEquals(-3L, MiniJson.parse("-3"))
        assertEquals(0.5, MiniJson.parse("0.5"))
        assertEquals(1e3, (MiniJson.parse("1e3") as Double), 0.0)
    }
}
