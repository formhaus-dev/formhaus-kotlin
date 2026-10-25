package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.regex.JsRegex
import dev.formhaus.core.support.Fixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlowPatternTest {
    private val letters =
        (0 until 150).joinToString("") { index ->
            val start = 0x100 + index * 5
            "${start.toChar()}-${(start + 2).toChar()}"
        }

    @Test
    fun `adjacent repeated parts that overlap are slow`() {
        val slow =
            listOf(
                "^\\d*\\d*\\d*\\d*\\d*x$",
                "a*a*b",
                ".*.*x",
                "\\d+\\w+",
                "[a-c]+[c-e]*",
                "(?:a|b)*a{2,}",
                "x(?:\\s*\\s+)y",
                "^\\d+\\.?\\d*$",
                "a+b{0,3}a+",
                "\\d+(?:-)?\\d+",
            )
        val fast = listOf("^\\d+-\\d+$", "[a-z]+@[a-z]+", "a*b*", "\\d*\\D*", "a?a?", "a+(?=a)a+", "\\(a*\\)a*")
        for (pattern in slow) assertNotNull(slowPattern(pattern), pattern)
        assertEquals(SlowPattern.ADJACENT, slowPattern(slow.first()))
        for (pattern in fast) assertNull(slowPattern(pattern), pattern)
        assertEquals(SlowPattern.NESTED, slowPattern("(\\w+\\s?)+"))
    }

    @Test
    fun `adversarial patterns are invalid`() {
        val patterns = listOf("(".repeat(500) + "+)+".repeat(500), "((a+)+".repeat(1000), "(a|".repeat(300) + "b")
        for (pattern in patterns) assertNull(JsRegex.compile(pattern), pattern.take(20))
    }

    @Test
    fun `patterns are scanned up to 1000 characters and longer ones are reported`() {
        val pattern = { length: Int -> "(a+)+" + "b".repeat(length - 5) }
        assertEquals(SlowPattern.NESTED, slowPattern(pattern(1000)))
        assertEquals(SlowPattern.LONG, slowPattern(pattern(1001)))
        val form =
            JsonValue.parse(
                """{"id":"f","title":"F","submit":{"label":"S"},"fields":[{"key":"p","type":"text","label":"P","validation":{"pattern":"${"a".repeat(
                    1001,
                )}"}}]}""",
            )
        assertEquals("Shorten it to 1000 characters or less.", checkDefinition(form).single().hint)
    }

    @Test
    fun `edge patterns get the verdicts of core`() {
        val rows = Fixtures.load(Fixtures.root.resolve("slow-patterns.json")).arrayValue.orEmpty()
        assertTrue(rows.size > 300)
        for (row in rows) {
            val items = row.arrayValue.orEmpty()
            val pattern = items[0].stringValue.orEmpty()
            assertEquals(items[1].stringValue, slowPattern(pattern)?.name?.lowercase(), pattern)
        }
    }

    @Test
    fun `repeated groups with a separator class that nothing else in them matches are not slow`() {
        val patterns =
            listOf(
                "^[A-Za-z]+([ '-][A-Za-z]+)*$",
                "^[A-Za-z]+(\\s[A-Za-z]+)*$",
                "^\\w+(?:\\s+\\w+)*$",
                "^[\\p{L}]+(?:[ '-][\\p{L}]+)*$",
                "^[\\p{L}0-9]+(?:[ &.,'()-]+[\\p{L}0-9]+)*$",
                "^[a-z0-9]+(?:[._-][a-z0-9]+)*@[a-z0-9]+(?:[.-][a-z0-9]+)*\\.[a-z]{2,}$",
                "^\\+?[0-9]{1,3}(?:[ -]?\\(?[0-9]{2,4}\\)?)?(?:[ -]?[0-9]{2,4}){2,3}$",
                "^#(?:[0-9a-fA-F]{3}){1,2}$",
                "^(?:\\d{3}-)+\\d{4}$",
                "^[A-Za-z$letters]+(?:[ '-][A-Za-z$letters]+)*$",
                "^[A-Za-z$letters]+(?:\\s+[A-Za-z$letters]+)*$",
                "^[A-Za-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u024F\u0300-\u065F\u066A-\u06EF\u06FA-\u06FF\u0900-\u0965" +
                    "\u0970-\u0E4F\u0E5A-\u0E7F\u1E00-\u1EFF\u3040-\u30FF\u4E00-\u9FFF\uAC00-\uD7A3]" +
                    "[A-Za-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u024F\u0300-\u065F\u066A-\u06EF\u06FA-\u06FF\u0900-\u0965" +
                    "\u0970-\u0E4F\u0E5A-\u0E7F\u1E00-\u1EFF\u3040-\u30FF\u4E00-\u9FFF\uAC00-\uD7A3 .,'\u2019()&\\-]{1,99}$",
            )
        for (pattern in patterns) {
            assertTrue(pattern.length <= 1000, pattern)
            assertNull(slowPattern(pattern), pattern)
        }
    }

    @Test
    fun `nested and overlapping repeats are slow`() {
        val patterns =
            listOf(
                "^(a+)+$" to SlowPattern.NESTED,
                "(\\w+\\s?)+" to SlowPattern.NESTED,
                "^[A-Za-z]+(?:[ '-]?[A-Za-z]+)*$" to SlowPattern.ADJACENT,
                "^(?:\\s+\\w*)*$" to SlowPattern.NESTED,
                "^(?:\\d?){1,50}$" to SlowPattern.NESTED,
                "^(?:\\w+|\\d+)+$" to SlowPattern.NESTED,
                "^\\d+\\d+$" to SlowPattern.ADJACENT,
                "^[\u00C0-\u00FF]+[\u00E0-\u00FF]+$" to SlowPattern.ADJACENT,
                "^(?:[\\u00c0-\\u00ff]+\\s?)+$" to SlowPattern.NESTED,
                "^(?:\\.a+)+(?:\\.a+)+$" to SlowPattern.ADJACENT,
            )
        for ((pattern, expected) in patterns) assertEquals(expected, slowPattern(pattern), pattern)
        assertEquals(SlowPattern.LONG, slowPattern("^[A-Za-z$letters$letters$letters]+$"))
    }
}
