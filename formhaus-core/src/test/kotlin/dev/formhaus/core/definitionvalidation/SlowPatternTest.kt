package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.regex.JsRegex
import dev.formhaus.core.server.isQuadratic
import dev.formhaus.core.support.Fixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlowPatternTest {
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
            assertEquals(items[2].boolValue, isQuadratic(pattern), pattern)
        }
    }
}
