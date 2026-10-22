package dev.formhaus.core.json

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JsonEncodingTest {
    @Test
    fun `parse, encode and parse again gives the same value`() {
        val text = """{"b":[1,2.5,-3,null,true,false],"a":{"x":"y","n":{}},"s":"line\nbreak \"q\" \\","e":[]}"""
        val value = JsonValue.parse(text)
        assertEquals(value, JsonValue.parse(value.toJsonString()))
        assertEquals(value, JsonValue.parse(value.toJsonBytes()))
        assertEquals(text, value.toJsonString())
    }

    @Test
    fun `numbers format like JSON stringify`() {
        val cases =
            listOf(
                1e21 to "1e+21",
                1e20 to "100000000000000000000",
                0.1 + 0.2 to "0.30000000000000004",
                -0.0 to "0",
                3.0 to "3",
                -12.0 to "-12",
                1.5 to "1.5",
                1e-7 to "1e-7",
                0.000001 to "0.000001",
                5e-324 to "5e-324",
                Double.NaN to "null",
                Double.POSITIVE_INFINITY to "null",
                Double.NEGATIVE_INFINITY to "null",
                2.82879384806159e17 to "282879384806159000",
            )
        for ((number, expected) in cases) assertEquals(expected, JsonValue.Number(number).toJsonString())
    }

    @Test
    fun `strings escape per RFC 8259`() {
        val text = "\"\\/\b\u000C\n\r\t\u0001\u001F\u007Fé😀\u2028"
        val encoded = JsonValue.String(text).toJsonString()
        assertEquals("\"\\\"\\\\/\\b\\f\\n\\r\\t\\u0001\\u001f\u007Fé😀\u2028\"", encoded)
        assertEquals(JsonValue.String(text), JsonValue.parse(encoded))
    }

    @Test
    fun `replacement characters stay valid UTF-8`() {
        val text = "\uFFFDA"
        val bytes = JsonValue.String(text).toJsonBytes()
        assertEquals(JsonValue.String(text), JsonValue.parse(bytes))
    }

    @Test
    fun `lone surrogates encode and parse as replacement characters`() {
        val cases =
            listOf(
                "\uD800" to "\uFFFD",
                "\uDC00" to "\uFFFD",
                "a\uD83D" to "a\uFFFD",
                "\uDE00\uD83D" to "\uFFFD\uFFFD",
                "\uD83D\uD83D\uDE00" to "\uFFFD\uD83D\uDE00",
                "\uD83D\uDE00" to "\uD83D\uDE00",
            )
        for ((text, expected) in cases) {
            val value = JsonValue.Array(listOf(JsonValue.String(text)))
            val encoded = value.toJsonString()
            assertEquals("[\"$expected\"]", encoded)
            assertEquals(JsonValue.parse(encoded), JsonValue.parse(value.toJsonBytes()))
            assertEquals(JsonValue.parse(encoded), JsonValue.parse("[\"$text\"]"))
            assertEquals(encoded, JsonValue.parse(encoded).toJsonString())
        }
    }

    @Test
    fun `integer-like keys come first in numeric order`() {
        val members = MutableJsonObject()
        for (key in listOf("b", "10", "a", "2", "01")) members[key] = JsonValue.Bool(true)
        assertEquals("""{"2":true,"10":true,"b":true,"a":true,"01":true}""", members.toJsonString())
        val parsed = JsonValue.parse("""{"b":1,"10":2,"2":3}""")
        assertEquals("""{"2":3,"10":2,"b":1}""", parsed.toJsonString())
    }

    @Test
    fun `JsonObject and JsonValue encode the same bytes`() {
        val members =
            stringMapOf(
                "email" to JsonValue.String("a@b.c"),
                "age" to JsonValue.Number(30),
                "tags" to JsonValue.Array(listOf(JsonValue.String("x"), JsonValue.Null)),
            )
        assertEquals("""{"email":"a@b.c","age":30,"tags":["x",null]}""", members.toJsonString())
        assertArrayEquals(JsonValue.Object(members).toJsonBytes(), members.toJsonBytes())
    }

    @Test
    fun `nested arrays encode to matching brackets`() {
        var value: JsonValue = JsonValue.Null
        repeat(300) { value = JsonValue.Array(listOf(value)) }
        assertEquals(604, value.toJsonString().length)
    }
}
