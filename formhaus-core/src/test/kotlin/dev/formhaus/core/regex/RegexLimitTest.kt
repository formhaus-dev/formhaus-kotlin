package dev.formhaus.core.regex

import dev.formhaus.core.support.Load
import dev.formhaus.core.validation.MAX_STRING_LENGTH
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RegexLimitTest {
    private fun regex(source: String): JsRegex = requireNotNull(JsRegex.compile(source)) { source }

    @Test
    fun `linear patterns finish inside the linear step budget on pathological input`() {
        val text = "a".repeat(MAX_STRING_LENGTH - 1) + "!"
        for (source in listOf(
            "(a+)+$",
            "(a|aa)+$",
            "^(a+)+$",
            "(.*a){20}$",
        )) {
            assertEquals(false, regex(source).test(text), source)
        }
        assertEquals(false, regex("(x+x+)+y").test("x".repeat(MAX_STRING_LENGTH)))
    }

    @Test
    fun `a backtracking match that runs out of steps fails closed`() {
        val pattern = regex("^(a|a)*\\1b$")
        StepBudget.backtrackingLimit.withValue(if (Load.heavy) StepBudget.backtrackingLimit.value else 100_000) {
            assertNull(pattern.test("a".repeat(40) + "cb"))
            assertEquals(true, pattern.test("aab"))
        }
    }

    @Test
    fun `lookarounds without backreferences are memoized`() {
        assertEquals(false, regex("^(?=(?:a|a)*b)(?:a|a)*c$").test("a".repeat(MAX_STRING_LENGTH - 1) + "c"))
    }

    @Test
    fun `deep nesting compiles on a secondary thread`() {
        val depth = RegexParser.MAX_DEPTH - 1
        val source = "(?:a|".repeat(depth) + "b" + ")".repeat(depth)
        val tooDeep = "(".repeat(RegexParser.MAX_DEPTH + 1) + ")".repeat(RegexParser.MAX_DEPTH + 1)
        var results: Pair<Boolean?, Boolean>? = null
        val thread =
            Thread(
                null,
                { results = JsRegex.compile(source)?.test("b") to (JsRegex.compile(tooDeep) == null) },
                "small-stack",
                512L * 1024,
            )
        thread.start()
        thread.join()
        assertEquals(true to true, results)
    }

    @Test
    fun `the cache keeps working past its limit`() {
        for (index in 0 until PatternCache.LIMIT + 50) assertEquals(true, JsRegex.compile("^p$index$")?.test("p$index"))
        assertEquals(true, JsRegex.compile("^p0$")?.test("p0"))
    }
}
