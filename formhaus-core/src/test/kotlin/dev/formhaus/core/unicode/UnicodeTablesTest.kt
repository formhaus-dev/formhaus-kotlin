package dev.formhaus.core.unicode

import dev.formhaus.core.json.isJsWhitespace
import dev.formhaus.core.regex.CaseFolding
import dev.formhaus.core.regex.JsRegex
import dev.formhaus.core.renderer.ErrorEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnicodeTablesTest {
    private fun hex(point: Int): String = "U+%04X".format(point)

    private fun codes(text: String): List<Int> = text.split(' ').map { it.toInt(16) }

    @Test
    fun `the tables carry the unicode version of the node that recorded the corpus`() {
        assertEquals("17.0", UNICODE_VERSION)
    }

    @Test
    fun `case folding matches v8`() {
        val expected =
            "61>41 7A>5A B5>39C DF>DF E0>C0 FF>178 131>131 17F>17F 1C5>1C4 1C6>1C4 345>399 390>390 3C2>3A3 " +
                "10D0>1C90 13F8>13F0 1C80>412 1C8A>1C89 1E9E>1E9E 1F80>1F80 212A>212A 2C5F>2C2F A7C1>A7C0 " +
                "A7CD>A7CC A7DB>A7DA 19B>A7DC 264>A7CB AB53>A7B3 D800>D800 FB00>FB00 FF41>FF21 6B>4B 4B>4B"
        for (pair in expected.split(' ')) {
            val (unit, canonical) = pair.split('>').map { it.toInt(16) }
            assertEquals(canonical, CaseFolding.canonical(unit).code, hex(unit))
        }
        assertEquals(true, JsRegex.unchecked("^(?i:\u019B)$").test("\uA7DC"))
        assertEquals(true, JsRegex.unchecked("^(?i:[\u1C89])$").test("\u1C8A"))
        assertEquals(false, JsRegex.unchecked("^(?i:k)$").test("\u212A"))
    }

    @Test
    fun `group names accept the identifier characters v8 accepts`() {
        val expected =
            "24:SP 30:P 41:SP 5F:SP B7:P 2118:SP 212E:SP 309B:SP 1885:SP 2E2F: 1F600: 10400:SP 1E900:SP " +
                "31350:SP 2EBF0:SP 323B0:SP 16D40:SP 11BC0:SP 1E5D0:SP 10940:SP 11DB0:SP 16EA0:SP 1E6C0:SP " +
                "200C:P 300:P 660:P FF10:P 3000: 1D7CE:P E0100:P"
        for (entry in expected.split(' ')) {
            val (code, accepted) = entry.split(':')
            val point = code.toInt(16)
            val char = String(Character.toChars(point))
            assertEquals('S' in accepted, JsRegex.compile("(?<$char>a)") != null, "start ${hex(point)}")
            assertEquals('P' in accepted, JsRegex.compile("(?<a$char>a)") != null, "part ${hex(point)}")
        }
    }

    @Test
    fun `recall keys take letters, marks and numbers as v8 does`() {
        for (point in codes("41 30 B2 300 2160 16D40 10940 660 E0100 1E6C0")) assertTrue(isLetterMarkNumber(point), hex(point))
        for (point in codes("5F 2D 1F600 3000 20")) assertFalse(isLetterMarkNumber(point), hex(point))
    }

    @Test
    fun `white space matches the v8 class`() {
        val spaces = codes("9 A B C D 20 A0 1680 2000 200A 2028 2029 202F 205F 3000 FEFF")
        for (unit in spaces + codes("85 180E 200B")) {
            assertEquals(unit in spaces, isJsWhitespace(unit.toChar()), hex(unit))
            assertEquals(unit in spaces, JsRegex.unchecked("^\\s$").test(unit.toChar().toString()), hex(unit))
        }
    }

    @Test
    fun `labels ending in compatibility punctuation take a space as after nfkc in v8`() {
        val expected =
            listOf(
                "Name?" to true,
                "Name" to false,
                "Name？" to true,
                "Done…" to true,
                "Ok。" to true,
                "Step ⑴" to false,
                "Wait⁉" to true,
                "1⒈" to true,
                "Note︓" to true,
                "Hi!\u0301" to false,
                "" to false,
            )
        for ((label, closing) in expected) {
            assertEquals(if (closing) "$label x" else "$label: x", ErrorEntry("k", label, "x").text, label)
        }
    }

    @Test
    fun `lowercase matches v8 including final sigma`() {
        val expected =
            listOf(
                "AΣʰ" to "aςʰ",
                "AΣ" to "aς",
                "Σ" to "σ",
                "AΣB" to "aσb",
                "AʰΣ" to "aʰς",
                "ʰΣ" to "ʰσ",
                "AΣ." to "aς.",
                "A.Σ" to "a.ς",
                "AΣ\u0345" to "aς\u0345",
                "A\u0345Σ" to "a\u0345ς",
                "AΣ\u0300 x" to "aς\u0300 x",
                "ΑΣΣ" to "ασς",
                "İ" to "i\u0307",
                "Iİ" to "ii\u0307",
                "K\u212A" to "kk",
                "ẞ" to "ß",
                "ΣΑΣ ΑΣ." to "σας ας.",
                "\uD801\uDC00\uD83A\uDD00" to "\uD801\uDC28\uD83A\uDD22",
                "plain" to "plain",
            )
        for ((text, lower) in expected) assertEquals(lower, jsLowercase(text), text)
    }
}
