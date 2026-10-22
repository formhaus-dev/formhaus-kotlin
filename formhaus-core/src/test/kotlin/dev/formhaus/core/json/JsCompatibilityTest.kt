package dev.formhaus.core.json

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsCompatibilityTest {
    @Test
    fun `numbers print like Number prototype toString`() {
        val cases =
            listOf(
                5.0 to "5",
                -0.0 to "0",
                0.1 to "0.1",
                123.456 to "123.456",
                1e-7 to "1e-7",
                0.000001 to "0.000001",
                1e21 to "1e+21",
                1e20 to "100000000000000000000",
                1.5e300 to "1.5e+300",
                -2.5 to "-2.5",
                Double.NaN to "NaN",
                Double.NEGATIVE_INFINITY to "-Infinity",
                9007199254740993.0 to "9007199254740992",
                1.7976931348623157e308 to "1.7976931348623157e+308",
                2.2250738585072014e-308 to "2.2250738585072014e-308",
            )
        for ((value, text) in cases) assertEquals(text, jsNumberString(value), "$value")
    }

    @Test
    fun `strings convert like Number()`() {
        assertEquals(42.0, jsNumber(" 42 "))
        assertEquals(5.0, jsNumber("5."))
        assertEquals(0.5, jsNumber(".5"))
        assertEquals(1000.0, jsNumber("+1e3"))
        assertEquals(16.0, jsNumber("0x10"))
        assertEquals(5.0, jsNumber("0B101"))
        assertEquals(0.0, jsNumber("  "))
        assertEquals(Double.NEGATIVE_INFINITY, jsNumber("-Infinity"))
        for (text in listOf(
            "1_000",
            "-0x10",
            "abc",
            "1e",
            "0x",
            "NaN",
            "1d",
            "0x1g",
        )) {
            assertTrue(jsNumber(text).isNaN(), text)
        }
    }

    @Test
    fun `trim removes JavaScript whitespace only`() {
        assertEquals("a", "\uFEFF\u00A0 a \u2028".jsTrimmed)
        assertEquals("\u200Ba", "\u200Ba".jsTrimmed)
    }

    @Test
    fun `collapse folds whitespace runs into one space`() {
        assertEquals("a b c", "a  b\t\nc".jsCollapsed)
        assertEquals("a b", "a b".jsCollapsed)
        assertEquals(" a ", "\u00A0a\u3000\u3000".jsCollapsed)
    }

    @Test
    fun `arrays convert like String()`() {
        val value = JsonValue.parse("""[1,[2,null,"x"],true,{}]""")
        assertEquals("1,2,,x,true,[object Object]", jsString(value))
        assertEquals("undefined", jsString(null))
    }
}
