package dev.formhaus.core.regex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RegexReverseTest {
    private val texts: List<String> =
        run {
            var state = 5L
            val alphabet = listOf("a", "b", "1", "x", " ", ",", "😀", "\n")
            val random =
                (0 until 160).map { index ->
                    (0 until index % 14).joinToString("") {
                        state = state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
                        alphabet[((state ushr 33) % alphabet.size).toInt()]
                    }
                }
            listOf(
                "",
                "a",
                "b",
                "1",
                "ab",
                "ba",
                "a1",
                "x1",
                "aab",
                "abc1",
                "a😀",
                "😀",
                "foo",
                " foo",
                "afoo",
                "xb1b1",
            ) +
                random
        }

    private fun forward(
        source: String,
        text: String,
    ): Boolean? = CompiledPattern("(?:$source)(?=)").test(text)

    private fun assertMatchesForward(source: String) {
        val pattern = CompiledPattern(source)
        for (text in texts) assertEquals(forward(source, text), pattern.test(text), "$source on $text")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "a$", "ab$", "(?:a|bc)$", "\\d+$", "(a+)+$", "[^]*a[^]{3}$", "x?$", "$", "(?:)$", "a*$", "\\b$", "a\\b$", "\\B$",
            "(?<=a)$", "(?<=ab)1$", "(?<!a)$", "(?<=^a?)b$", "^a$|b$", "(?:^|,)x$", "(?=a)\\w+$", "(?!1)..$", "(?:a$)+",
            "\\uD83D$", "\\uDE00$", ".$", "[\\uD800-\\uDBFF]$", "(?:b|$)$", "a$(?:z){0}",
        ],
    )
    fun `end-anchored patterns run from the end`(source: String) {
        assertEquals(SearchPlan.FromEnd, CompiledPattern(source).plan)
        assertMatchesForward(source)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "[a-z]{0,40}1", "a[b-z]*1", "x[^]{0,20}1", "ab", "(?:ab|1)b", "\\bfoo", "(?<=a)1", "(?<!x)1", "[ab]*a[ab]{2}1", "(?:a|)1",
            "1(?:){0}", "x(?:1)",
        ],
    )
    fun `patterns that end in a literal run back from each occurrence`(source: String) {
        assertTrue(CompiledPattern(source).plan is SearchPlan.Suffix)
        assertMatchesForward(source)
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["^a$", "(?m:a$)", "a$(?=)", "(a)\\1$", "(a)\\1", "a|b$", "foo\\b", "(?i:a)", "a$|b", "^(?:a|b)1"],
    )
    fun `other patterns keep the forward search`(source: String) {
        assertEquals(SearchPlan.Forward, CompiledPattern(source).plan)
    }

    @Test
    fun `a reverse scan that reaches the previous occurrence falls back to a full search`() {
        val pattern = CompiledPattern("x[^]{0,20}1")
        val text = "bb1".repeat(3000)
        assertEquals(true, pattern.test(text + "x1"))
        assertEquals(false, pattern.test(text + "1"))
        assertEquals(false, pattern.test(text + "x" + "b".repeat(30) + "1"))
        assertEquals(true, pattern.test("x$text"))
    }

    @Test
    fun `large end-anchored repeats decide from the end`() {
        val pattern = CompiledPattern("[^]*a[^]{3900}$")
        val tail = "b".repeat(3900)
        assertEquals(true, pattern.test("b".repeat(6000) + "a" + tail))
        assertEquals(false, pattern.test("a".repeat(6000) + "b" + tail))
        assertEquals(false, pattern.test("a" + tail.dropLast(1)))
        val counted = CompiledPattern("[a-z]{0,4001}1")
        assertEquals(true, counted.test("b".repeat(6000) + "1"))
        assertEquals(false, counted.test("b".repeat(6000) + "2"))
    }
}
