package dev.formhaus.core.regex

import dev.formhaus.core.support.Load
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
}
