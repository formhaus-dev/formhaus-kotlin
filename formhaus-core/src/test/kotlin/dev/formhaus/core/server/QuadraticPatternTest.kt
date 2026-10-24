package dev.formhaus.core.server

import dev.formhaus.core.definitionvalidation.AsciiCounts
import dev.formhaus.core.definitionvalidation.AsciiSet
import dev.formhaus.core.definitionvalidation.FormDefinitionError
import dev.formhaus.core.definitionvalidation.IssueCode
import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.support.field
import dev.formhaus.core.support.jsonObject
import dev.formhaus.core.validation.validateFieldValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class QuadraticPatternTest {
    private fun text(
        key: String,
        pattern: String,
    ): String = """{"key":"$key","type":"text","label":"T","validation":{"pattern":${JsonValue.String(pattern)}}}"""

    private fun form(vararg fields: String): JsonValue =
        JsonValue.parse("""{"id":"f","title":"F","submit":{"label":"S"},"fields":[${fields.joinToString(",")}]}""")

    private fun refused(definition: JsonValue): List<String> =
        try {
            validateSubmission(definition, JsonValue.Object(MutableJsonObject()))
            emptyList()
        } catch (error: FormDefinitionError) {
            error.issues.map { it.path }
        }

    @Test
    fun `the allowlist takes one overlapping repeat after a caret`() {
        val quadratic =
            listOf(
                "^\\d+\\.?\\d*$",
                "^[0-9]+\\s?[0-9]+$",
                "^(\\d+)\\.?(\\d*)$",
                "^[A-Za-z]+\\s?[A-Za-z]*$",
                "^\\d+(\\d*)$",
                "^\\d+\\.?\\d*\\$",
                "^[a-z-]+[a-z]*1",
                "^(?:\\w+)\\w*",
                "^\\d+\\.?\\d?\\d*$",
                "^\\d+\\d*",
            )
        val other =
            listOf(
                "\\d+\\.?\\d*$",
                "^\\d*\\d*\\d*$",
                "^\\d+\\D?\\d*$",
                "^\\d+\u00E9?\\d*$",
                "^\\d+?\\d*$",
                "^\\d+\\d*?$",
                "^\\d{1,3}\\d*$",
                "^(\\d+)+$",
                "^\\d+(\\d*)",
                "^[z-a]+a*$",
                "^[\\d-z]+\\d*$",
                "^[^a]+a*$",
                "^\\d+$|^x\\d*$",
                "^\\d+1\\d*$",
                "^(\\d+(\\d*))$",
                "^\\d+[\\b]\\d*$",
                "^\\d+\\d*\\\\$",
                "^\\d+\\d*(",
                "^[a-z-]+[a-z]*x",
            )
        for (pattern in quadratic) assertTrue(isQuadratic(pattern), pattern)
        for (pattern in other) assertFalse(isQuadratic(pattern), pattern)
    }

    @Test
    fun `verdicts follow the slow-pattern check and the allowlist`() {
        assertEquals(PatternVerdict.SAFE, patternVerdict("^\\d+$"))
        assertEquals(PatternVerdict.QUADRATIC, patternVerdict("^\\d+\\.?\\d*$"))
        assertEquals(PatternVerdict.REFUSED, patternVerdict("^(a+)+$"))
        assertEquals(PatternVerdict.REFUSED, patternVerdict("a".repeat(1001)))
        assertEquals(PatternVerdict.SAFE, patternVerdict("a".repeat(1000)))
        assertEquals(PatternVerdict.REFUSED, patternVerdict("\\d+\u00E9?\\d*"))
        assertEquals(PatternVerdict.SAFE, patternVerdict("\\d+e\u0301?\\d*"))
    }

    @Test
    fun `refused patterns are reported at their path, also inside groups and steps`() {
        val group = """{"key":"people","type":"group","label":"P","fields":[${text("name", "^(a+)+$")}]}"""
        assertEquals(listOf("/fields/1/fields/0/validation/pattern"), refused(form(text("a", "^\\d+$"), group)))
        val steps =
            JsonValue.parse(
                """{"id":"f","title":"F","submit":{"label":"S"},"steps":[{"id":"one","fields":[${text("a", "^\\d+\\.?\\d*$")}]},""" +
                    """{"id":"two","fields":[${text("b", "(x*)*")}]}]}""",
            )
        assertEquals(listOf("/steps/1/fields/0/validation/pattern"), refused(steps))
        val error =
            assertThrows<FormDefinitionError> {
                validateSubmission(form(text("long", "a".repeat(1001))), JsonValue.Object(MutableJsonObject()))
            }
        val issue = error.issues.single()
        assertEquals(IssueCode.SLOW_PATTERN, issue.code)
        assertTrue(issue.isError)
        val problem = "the pattern of \"long\" can take very long on some values, so the server does not run it."
        val hint = "Rewrite it without nested or overlapping repeats, in 1000 characters or less."
        assertEquals("/fields/0/validation/pattern: $problem $hint", issue.message)
    }

    @Test
    fun `the engine accepts refused patterns`() {
        val engine = FormEngine(FormDefinition(form(text("a", "^(a+)+$"))), jsonObject("""{"a":"aaa"}"""))
        assertTrue(engine.validate().isEmpty())
    }

    @Test
    fun `the server checks quadratic patterns on values up to 1000 characters`() {
        val digits = """{"key":"a","type":"text","label":"T","validation":{"pattern":"^\\d+\\.?\\d*$","patternMessage":"Digits"}}"""
        val definition = form(digits, text("b", "^\\d+$"))
        val long = "1".repeat(1001)
        val short = "1".repeat(1000)
        val accepted = validateSubmission(definition, JsonValue.parse("""{"a":"$short","b":"$long"}"""))
        assertTrue(accepted.errors.isEmpty())
        val rejected = validateSubmission(definition, JsonValue.parse("""{"a":"$long","b":"$long"}"""))
        assertEquals(mapOf("a" to "Digits"), rejected.errors.toMap())
        val engine = FormEngine(FormDefinition(definition), jsonObject("""{"a":"$long"}"""))
        assertTrue(engine.validate().isEmpty())
        assertNull(validateFieldValue(field(digits), JsonValue.String(long), MutableJsonObject()))
    }

    @Test
    fun `group items and multiselect values over 1000 characters get the pattern error on the server`() {
        val first = "1".repeat(600)
        val second = "2".repeat(600)
        val group = """{"key":"people","type":"group","label":"P","fields":[${text("name", "^\\d+\\.?\\d*$")}]}"""
        val tags =
            """{"key":"tags","type":"multiselect","label":"T","options":[{"value":"$first","label":"A"},""" +
                """{"value":"$second","label":"B"}],"validation":{"pattern":"^[0-9,]+\\.?[0-9,]*$"}}"""
        val definition = form(group, tags)
        val long = "1".repeat(1001)
        val values = """{"people":[{"name":"$long"},{"name":"${long.drop(1)}"}],"tags":["$first","$second"]}"""
        val result = validateSubmission(definition, JsonValue.parse(values))
        assertEquals(mapOf("people.0.name" to "Invalid format", "tags" to "Invalid format"), result.errors.toMap())
        val short = validateSubmission(definition, JsonValue.parse("""{"people":[{"name":"12"}],"tags":["$first"]}"""))
        assertTrue(short.errors.isEmpty())
        assertTrue(FormEngine(FormDefinition(definition), jsonObject(values)).validate().isEmpty())
    }

    @Test
    fun `verdicts agree when computed concurrently`() {
        val patterns = (0 until 64).map { "^\\d+\\.?[\\d${it % 10}]*$" } + (0 until 64).map { "(a{$it}+)+" }
        val serial = patterns.map(::patternVerdict)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val concurrent = pool.invokeAll(patterns.map { Callable { patternVerdict(it) } }).map { it.get() }
            assertEquals(serial, concurrent)
        } finally {
            pool.shutdown()
        }
        assertTrue(PatternVerdict.QUADRATIC in serial && PatternVerdict.REFUSED in serial)
    }

    @Test
    fun `ASCII sets count and name single members`() {
        var set = AsciiSet()
        assertTrue(set.isEmpty)
        assertNull(set.single)
        set = set.with('\u007F')
        assertEquals('\u007F', set.single)
        assertTrue('\u007F' in set)
        assertFalse('?' in set)
        set = set.with('\u0000').with('\u0100')
        assertEquals(2, set.size)
        assertNull(set.single)
        assertFalse('\u0100' in set)
        assertEquals(128, AsciiSet.ALL.size)
        val counts = AsciiCounts() + AsciiSet.of(".")
        assertTrue(counts.appearsOnce('.'))
        val doubled = counts + counts
        assertFalse(doubled.appearsOnce('.'))
        assertTrue('.' in doubled.once)
    }
}
