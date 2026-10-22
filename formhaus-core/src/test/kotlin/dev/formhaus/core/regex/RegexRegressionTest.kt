package dev.formhaus.core.regex

import dev.formhaus.core.support.Load
import dev.formhaus.core.validation.MAX_STRING_LENGTH
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class RegexRegressionTest {
    companion object {
        fun randomText(count: Int, alphabet: String, seed: Long): String {
            var state = seed
            return String(CharArray(count) {
                state = state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
                alphabet[((state ushr 33) % alphabet.length).toInt()]
            })
        }

        @JvmStatic
        fun clearingSizes(): List<Int> = Load.sizes(1000, heavy = 10000)

        @JvmStatic
        fun referenceSizes(): List<Int> = Load.sizes(400, heavy = 4000)

        @JvmStatic
        fun repeatSizes(): List<Int> = Load.sizes(401, heavy = 4001)

        @JvmStatic
        fun memorySizes(): List<Int> = Load.sizes(4, heavy = 40)

        @JvmStatic
        fun passes(): List<Int> = Load.sizes(1, heavy = 12)

        @JvmStatic
        fun pools(): List<Arguments> =
            if (Load.heavy) (0 until 3).map { Arguments.of(it, 50, AutomatonMemory.GLOBAL_LIMIT) } else listOf(Arguments.of(0, 8, 4 shl 20))

        @JvmStatic
        fun duplicateNames(): List<Arguments> = listOf(
            Arguments.of("(?<n>)(?:(x)|y)(?:z|(?<n>a))", false),
            Arguments.of("(?<n>a)(?:b|(?<n>c))", true),
            Arguments.of("(?:(?<n>a)|(?<n>b))", true),
            Arguments.of("(?<n>a)|(?:b|(?<n>c))", true),
            Arguments.of("((?<n>a)|(?<n>b))(?:c|(?<n>d))", false),
            Arguments.of("(?<n>x)|(?:(?<n>y)(?:z|(?<n>w)))", true),
        )

        @JvmStatic
        fun literalTexts(): List<Arguments> = listOf(
            Arguments.of("x\u0161abcab", true), Arguments.of("abcaabcab", true), Arguments.of("abcab", true),
            Arguments.of("abca", false), Arguments.of("\u0161bcab", false), Arguments.of("abcxabcab!", true),
        )

        @JvmStatic
        fun largeRepeats(): List<Arguments> = listOf(
            Arguments.of("^.{0,16384}$", true), Arguments.of("^[\\s\\S]{0,65535}$", true), Arguments.of("^[^<>]{0,20000}$", true),
            Arguments.of("^.{1,50000}$", true), Arguments.of("^(?:[a-z]{2,8}-){0,4000}$", false), Arguments.of("^(\\w{1,200} ?){1,200}$", true),
        )
    }

    private fun regex(source: String): JsRegex = requireNotNull(JsRegex.compile(source)) { source }

    @Test
    fun `a reset automaton keeps its start state`() {
        val pattern = regex("[ab]*a[ab]{12}$(?=)")
        pattern.test(randomText(MAX_STRING_LENGTH, "ab", 7))
        assertEquals(false, pattern.test("a"))
        assertEquals(false, pattern.test("ab"))
        assertEquals(true, pattern.test("a" + "b".repeat(12)))
        assertEquals(false, pattern.test("b".repeat(13)))
    }

    @ParameterizedTest
    @MethodSource("clearingSizes")
    fun `clearing many captures counts toward the step budget`(groups: Int) {
        val pattern = regex("(?:a|[]" + "()".repeat(groups) + ")*\\1(?!)")
        val text = "a".repeat(MAX_STRING_LENGTH - 1)
        StepBudget.backtrackingLimit.withValue(groups * 100) { assertNull(pattern.test(text)) }
    }

    @ParameterizedTest
    @MethodSource("referenceSizes")
    fun `unset duplicate groups in a backreference count toward the step budget`(count: Int) {
        val pattern = regex("(?:" + "(?<n>a)|".repeat(count) + "b)(?:\\k<n>c)*$")
        val text = "b" + "c".repeat(MAX_STRING_LENGTH - 1)
        StepBudget.backtrackingLimit.withValue(count * 250) { assertNull(pattern.test(text)) }
    }

    @ParameterizedTest
    @MethodSource("duplicateNames")
    fun `duplicate group names follow V8 alternatives`(source: String, valid: Boolean) {
        assertEquals(valid, JsRegex.compile(source) != null, source)
    }

    @Test
    fun `nested lookarounds run in linear time`() {
        val unanchored = regex("(?=(?!b)[^]*x)y(?:z|$)")
        val anchored = regex("^(?=(?!b)[^]*x)y+x$")
        val long = "y".repeat(MAX_STRING_LENGTH - 1) + "x"
        val short = "y".repeat(MAX_STRING_LENGTH / 8 - 1) + "x"
        assertEquals(false, unanchored.test(long))
        assertEquals(true, anchored.test(long))
        assertEquals(false, unanchored.test(short))
        assertEquals(true, anchored.test(short))
    }

    @Test
    fun `a pattern past the size limit fails closed`() {
        val optional = (RegexCompiler.INSTRUCTION_LIMIT - 2) / 2
        assertEquals(true, regex("a" + "b?".repeat(optional)).test("ab"))
        assertNull(regex("a" + "b?".repeat(optional + 1)).test("ab"))
        val literal = "a".repeat(RegexCompiler.INSTRUCTION_LIMIT)
        assertNull(regex(literal).test(literal))
        assertEquals(false, regex(literal).test("a"))
        assertEquals(false, regex("(?:a{1000}){1000}").test("aaaa"))
    }

    @ParameterizedTest
    @MethodSource("repeatSizes")
    fun `large counted repeats match every input up to the length limit`(count: Int) {
        val half = (count + 1) / 2
        val long = "b".repeat(count * 3 / 2)
        assertEquals(true, regex("[a-z]{0,$count}1").test(long + "1"))
        assertEquals(false, regex("[a-z]{0,$count}1").test(long + "2"))
        assertEquals(true, regex("(?:x?){$count}y").test("xay"))
        assertEquals(true, regex("(?:a|b){$count}c").test("ab".repeat(half) + "c"))
        assertEquals(true, regex("^(?:a{0,2}){$half}$").test("a".repeat(2 * half)))
    }

    @ParameterizedTest
    @MethodSource("memorySizes")
    fun `automaton memory stays inside the pattern and global limits`(offsets: Int) {
        for (offset in 0 until offsets) {
            val width = 300 - offset
            val compiled = CompiledPattern("[^]*a[^]{$width}$(?=)")
            val text = randomText(1600, "ab", offset + 1L)
            assertEquals(text[1600 - width - 1] == 'a', compiled.test(text))
            assertEquals(text.startsWith("a"), compiled.test(text.take(width + 1)))
            assertTrue(requireNotNull(compiled.automaton).bytes <= AutomatonMemory.PATTERN_LIMIT)
            assertTrue(AutomatonMemory.reservedTotal <= AutomatonMemory.GLOBAL_LIMIT)
        }
    }

    @ParameterizedTest
    @MethodSource("pools")
    fun `a full memory pool reclaims states from automatons that sat idle`(round: Int, count: Int, limit: Int) {
        val pool = MemoryPool(limit)
        MemoryPool.current.withValue(pool) {
            val letter = "cde"[round]
            val patterns = (0 until count).map { CompiledPattern("[^]*$letter[^]{${300 - it}}$(?=)") }
            for ((index, pattern) in patterns.withIndex()) {
                pattern.test(randomText(1300, "${letter}b", index + 1L))
                assertTrue(AutomatonMemory.reservedTotal <= pool.limit)
            }
            assertTrue(requireNotNull(patterns.last().automaton).bytes > AutomatonMemory.PATTERN_LIMIT / 2)
            val fresh = CompiledPattern("^[a-z]+@[a-z]+\\.$letter[a-z]{2,}$")
            assertEquals(true, fresh.test("ada@example.${letter}om"))
            assertTrue(requireNotNull(fresh.automaton).bytes > 0)
        }
    }

    @ParameterizedTest
    @MethodSource("passes")
    fun `threads share automatons without changing results`(passes: Int) {
        val sources = listOf(
            "[ab]*a[ab]{12}$", "[ab]*a[ab]{12}$(?=)", "[^]*a[^]{100}$", "(?=[ab]*a[ab]{10}$)[ab]+", "(?<=a[ab]{9})b$",
            "^(?:a|b)*a(?:a|b){11}$",
        )
        val texts = (0 until 6).map { randomText(if (it % 2 == 0) 20 else 800, "ab", it + 11L) }
        val expected = sources.map { source -> texts.map { CompiledPattern(source).test(it) } }
        val patterns = sources.map(::regex)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val workers = (0 until 8).map { worker ->
                Callable {
                    var mismatches = 0
                    for (round in 0 until passes) {
                        for (index in patterns.indices) {
                            val pattern = (index + worker + round) % patterns.size
                            for ((text, result) in texts.zip(expected[pattern])) if (patterns[pattern].test(text) != result) mismatches++
                        }
                    }
                    mismatches
                }
            }
            assertEquals(0, executor.invokeAll(workers).sumOf { it.get() })
        } finally {
            executor.shutdownNow()
        }
    }

    @ParameterizedTest
    @MethodSource("literalTexts")
    fun `the required literal search finds every occurrence`(text: String, expected: Boolean) {
        assertEquals(expected, regex("abcab").test(text))
        assertEquals(expected, regex("[a-z]*abcab").test(text))
    }

    @ParameterizedTest
    @MethodSource("largeRepeats")
    fun `counted repeats larger than any input still match`(source: String, expected: Boolean) {
        val pattern = regex(source)
        assertEquals(expected, pattern.test("hello world"))
        if (!source.startsWith("^(\\w")) assertNull(pattern.test("a".repeat(MAX_STRING_LENGTH + 1)))
    }

    @Test
    fun `nested counted repeats find a match on long input`() {
        assertEquals(true, regex("^(\\w{1,200} ?){1,200}$").test("a".repeat(MAX_STRING_LENGTH - 1) + "b"))
    }

    @Test
    fun `nested empty repeats with backreferences fail closed on short values`() {
        val source = "(?:((\\1{0,1}\\1[\\s\\S]?){2}(?i:\\1[^a]{0,1}|\\uFEFF{1,}?|\\b\\1{1,3}?\\b){0})*(?i:(?:)K|))+[ab]"
        val pattern = regex(source)
        StepBudget.backtrackingLimit.withValue(if (Load.heavy) StepBudget.backtrackingLimit.value else 50_000) {
            assertNull(pattern.test("a\u212A-_\uFEFF\u00E9x1"))
        }
    }

    @Test
    fun `the pattern cache stays inside its byte limit`() {
        val cache = PatternCache(byteLimit = 4 shl 20)
        for (index in 0 until 12) {
            assertEquals(false, cache.pattern("a$index" + "b?".repeat(16000))?.test("a"))
            assertTrue(cache.bytes <= 4 shl 20)
        }
        assertTrue(cache.bytes > 2 shl 20)
        assertEquals(true, cache.pattern("^ok$")?.test("ok"))
        val huge = "x" + "y?".repeat(100_000)
        val before = cache.bytes
        assertEquals(true, cache.pattern(huge)?.test("xy"))
        assertEquals(before, cache.bytes)
    }
}
