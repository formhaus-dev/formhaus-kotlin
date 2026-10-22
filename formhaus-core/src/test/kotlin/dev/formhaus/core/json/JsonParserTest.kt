package dev.formhaus.core.json

import dev.formhaus.core.support.Load
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class JsonParserTest {
    companion object {
        @JvmStatic
        fun depths(): List<Int> = Load.sizes(20_000, heavy = 200_000)
    }

    private fun onSmallStack(body: () -> String): String {
        var result = ""
        val thread = Thread(null, { result = body() }, "small-stack", 512L * 1024)
        thread.start()
        thread.join()
        return result
    }

    private fun nested(depth: Int) = "[".repeat(depth) + "]".repeat(depth)

    @ParameterizedTest
    @MethodSource("depths")
    fun `deeply nested arrays fail cleanly on a background thread`(depth: Int) {
        val text = nested(depth)
        val outcome = onSmallStack {
            try {
                JsonValue.parse(text)
                "parsed"
            } catch (error: JsonParseException) {
                error.reason
            }
        }
        assertEquals("nesting deeper than 512 levels", outcome)
    }

    @Test
    fun `the deepest accepted document parses, compares and prints on a background thread`() {
        val text = nested(JsonValue.MAX_NESTING_DEPTH)
        val outcome = onSmallStack {
            val value = JsonValue.parse(text)
            val copy = JsonValue.parse(text)
            val same = value == copy && value.hashCode() == copy.hashCode()
            if (same && jsonStringify(value) == text) "ok" else "mismatch"
        }
        assertEquals("ok", outcome)
        assertThrows<JsonParseException> { JsonValue.parse("{\"a\":${nested(JsonValue.MAX_NESTING_DEPTH)}}") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["\"\\uD800\"", "\"\\uDC00\"", "\"\\uD800A\"", "\"\\uD800x\"", "\"\\uDBFF\\uDBFF\""])
    fun `lone surrogates are rejected`(text: String) {
        assertThrows<JsonParseException> { JsonValue.parse(text) }
    }

    @Test
    fun `escaped surrogate pairs and malformed escapes`() {
        assertEquals(JsonValue.String("😀"), JsonValue.parse("\"\\uD83D\\uDE00\""))
        assertEquals(JsonValue.String("😀"), JsonValue.parse("\"😀\""))
        assertThrows<JsonParseException> { JsonValue.parse("\"\\u+123\"") }
        assertThrows<JsonParseException> { JsonValue.parse("\"\\u12\"") }
    }

    @Test
    fun `parser keeps key order and own __proto__ keys`() {
        val value = JsonValue.parse("""{"b":1,"__proto__":{"x":true},"a":[null,"é😀"],"b":2}""")
        val members = requireNotNull(value.objectValue)
        assertEquals(listOf("b", "__proto__", "a"), members.keys)
        assertEquals(JsonValue.Number(2), members["b"])
        assertEquals(JsonValue.Array(listOf(JsonValue.Null, JsonValue.String("é😀"))), members["a"])
        for (text in listOf("{\"a\":01}", "[1,]", "{\"a\":1,}", "[1] x")) {
            assertThrows<JsonParseException> { JsonValue.parse(text) }
        }
        val expected = JsonValue.Array(listOf(JsonValue.Object(MutableJsonObject()), JsonValue.Array(emptyList()), JsonValue.Number(-5)))
        assertEquals(expected, JsonValue.parse(" [ { } , [ ] , -0.5e1 ] "))
    }

    @Test
    fun `errors report the byte offset`() {
        val error = assertThrows<JsonParseException> { JsonValue.parse("[1, x]") }
        assertEquals(4, error.offset)
        assertEquals("Invalid JSON at byte 4: unexpected token", error.message)
    }
}
