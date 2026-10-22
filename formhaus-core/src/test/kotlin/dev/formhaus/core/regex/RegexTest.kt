package dev.formhaus.core.regex

import dev.formhaus.core.support.Load
import dev.formhaus.core.validation.MAX_STRING_LENGTH
import dev.formhaus.core.validation.rejects
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RegexTest {
    companion object {
        @JvmStatic
        fun matches(): List<Arguments> =
            listOf(
                Arguments.of("", "x", true),
                Arguments.of("^[\\w-.]+$", "a.b-c", true),
                Arguments.of("(?<=a+)b", "aab", true),
                Arguments.of("^\\1$", "\u0001", true),
                Arguments.of("^\\8$", "8", true),
                Arguments.of("^\\101$", "A", true),
                Arguments.of("^\\k$", "k", true),
                Arguments.of("^\\01$", "\u0001", true),
                Arguments.of("^[\u093E\u093F\u0940\u0941]+$", "\u093E\u093F", true),
                Arguments.of("^[\u093E\u093F\u0940\u0941]+$", "\u093E\u0915", false),
                Arguments.of("^.{1,3}$", "😀😀", false),
                Arguments.of("^.{1,3}$", "😀", true),
                Arguments.of("^[:digit:]$", "5", false),
                Arguments.of("^[:digit:]$", ":", true),
                Arguments.of("a++", "a", null),
                Arguments.of("(?i)abc", "ABC", null),
                Arguments.of("[😀-😂]", "😁", null),
                Arguments.of("(?=a)*a", "a", true),
                Arguments.of("^\\d+$", "123", true),
                Arguments.of("^\\d+$", "123\n", false),
                Arguments.of("^\\d$", "\u0663", false),
                Arguments.of("^\\w$", "é", false),
                Arguments.of("^a{$", "a{", true),
                Arguments.of("^[^]$", "\n", true),
                Arguments.of("^.$", "\n", false),
                Arguments.of("^\\a$", "a", true),
                Arguments.of("[(a+)]+", "+", true),
                Arguments.of("[invalid(", "x", null),
                Arguments.of("(a", "a", null),
                Arguments.of("(?i:b|[ab])", "A", true),
                Arguments.of("(?i:x|[a-c])", "A", true),
                Arguments.of("^(?i:\\u0178)$", "\u00FF", true),
                Arguments.of("(?i:((3|([a]))))", "A", true),
                Arguments.of("(?i:(?=\\W{2}|(W))(A|(?<![a])))", "A\uD83D\uDE00", false),
            )
    }

    @ParameterizedTest
    @MethodSource("matches")
    fun `pattern test results`(
        source: String,
        text: String,
        expected: Boolean?,
    ) {
        val pattern = JsRegex.compile(source)
        if (expected == null) {
            assertNull(pattern, "$source compiles")
        } else {
            assertEquals(expected, pattern?.test(text), "$source on $text")
        }
    }

    @Test
    fun `pattern source is never evaluated as code`() {
        val source = "a/.test(\"\"), globalThis.pwned = 1, /a"
        assertEquals(true, JsRegex.compile(source)?.test("a/Xtest\"\", globalThis.pwned = 1, /a"))
        assertNull(JsRegex.compile("\")+(1"))
    }

    @Test
    fun `a pattern that cannot run rejects the value`() {
        assertNull(JsRegex.unchecked("(").test("a"))
        assertTrue(rejects(JsRegex.unchecked("("), "a"))
        assertTrue(rejects(requireNotNull(JsRegex.compile("^a$")), "a".repeat(MAX_STRING_LENGTH + 1)))
        assertFalse(rejects(requireNotNull(JsRegex.compile("^a+$")), "a".repeat(MAX_STRING_LENGTH)))
    }

    @Test
    fun `a slow match does not block other threads`() {
        assumeTrue(Load.heavy)
        val slow = requireNotNull(JsRegex.compile("^(a|a)*\\1b$"))
        val text = "a".repeat(40) + "cb"
        val finished = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val blocked = executor.submit(Callable { slow.test(text).also { finished.set(true) } })
            Thread.sleep(50)
            assertEquals(true, JsRegex.compile("^b$")?.test("b"))
            assertFalse(finished.get())
            assertNull(blocked.get(10, TimeUnit.MINUTES))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `patterns compile and match from many threads`() {
        val executor = Executors.newFixedThreadPool(8)
        try {
            val tasks =
                (0 until 64).map { index ->
                    Callable { JsRegex.compile("^x${index % 300}$")?.test("x${index % 300}") == true }
                }
            for (result in executor.invokeAll(tasks)) assertTrue(result.get())
        } finally {
            executor.shutdownNow()
        }
    }
}
