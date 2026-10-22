package dev.formhaus.core.regex

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.support.Load
import dev.formhaus.core.support.definition
import dev.formhaus.core.validation.ValidateOptions
import dev.formhaus.core.validation.ValidatorFn
import dev.formhaus.core.validation.validateFieldValue
import dev.formhaus.core.validation.validateFields
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegexBudgetTest {
    private val heavy = "^(?:a?b?c?d?e?f?g?h?){10000}$"
    private val heavyText = "a".repeat(9990) + "!"

    @Test
    fun `canonically equivalent patterns keep separate cache entries`() {
        val cache = PatternCache()
        assertEquals(true, cache.pattern("^[^e\u0301]+$")?.test("x"))
        assertEquals(false, cache.pattern("^[^\u00E9]+$")?.test("\u00E9"))
        assertEquals(true, cache.pattern("^[^e\u0301]+$")?.test("\u00E9"))
        assertEquals(true, JsRegex.compile("^[^e\u0301]+$")?.test("x"))
        assertEquals(false, JsRegex.compile("^[^\u00E9]+$")?.test("\u00E9"))
    }

    @Test
    fun `a linear match stops at the step limit and fails closed`() {
        val pattern = CompiledPattern(heavy)
        StepBudget.backtrackingLimit.withValue(if (Load.heavy) StepBudget.backtrackingLimit.value else 4_000_000) {
            assertNull(pattern.test(heavyText))
            assertEquals(true, pattern.test("abcdefgh"))
        }
    }

    @Test
    fun `one budget covers every pattern in a call`() {
        val pattern = CompiledPattern(heavy)
        val simple = CompiledPattern("^a+$")
        CallBudget.current.withValue(CallBudget(200_000)) {
            assertEquals(true, simple.test("aaa"))
            assertNull(pattern.test(heavyText))
            assertNull(simple.test("aaa"))
        }
        assertEquals(true, simple.test("aaa"))
    }

    @Test
    fun `public validators run in their own budget`() {
        val probe =
            stringMapOf<ValidatorFn>("probe" to { _, _ -> if (CallBudget.current.value == null) "outside" else null })
        val form =
            definition(
                """{"id":"p","submit":{"label":"S"},"fields":[{"key":"a","type":"text","label":"A","validation":{"validator":"probe"}}]}""",
            )
        val nodes = requireNotNull(form.fields)
        assertNull(
            validateFieldValue(form.allFields.first(), JsonValue.String("x"), stringMapOf(), ValidateOptions(probe)),
        )
        assertTrue(validateFields(nodes, stringMapOf("a" to JsonValue.String("x")), ValidateOptions(probe)).isEmpty())
    }

    @Test
    fun `one validation tests at most 10,000 values against patterns`() {
        val limit = CallBudget.patternTestLimit.value
        val fields =
            (0..limit).joinToString(",") {
                """{"key":"f$it","type":"text","label":"F","validation":{"pattern":"^a$"}}"""
            }
        val form = definition("""{"id":"p","submit":{"label":"S"},"fields":[$fields]}""")
        val nodes = requireNotNull(form.fields)
        val values = MutableJsonObject()
        for (index in 0..limit) values["f$index"] = JsonValue.String("a")
        repeat(2) { assertEquals(stringMapOf("f$limit" to "Invalid format"), validateFields(nodes, values)) }
        val field = form.allFields.first()
        assertTrue((0..limit).all { validateFieldValue(field, JsonValue.String("a"), values) == null })
    }
}
