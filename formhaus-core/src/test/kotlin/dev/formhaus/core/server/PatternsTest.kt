package dev.formhaus.core.server

import dev.formhaus.core.definitionvalidation.checkDefinition
import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.support.Fixtures
import dev.formhaus.core.support.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PatternsTest {
    private val corpus = Fixtures.load(Fixtures.root.resolve("real-world-patterns.json")).arrayValue.orEmpty()

    private fun field(
        key: String,
        pattern: String,
    ): String = """{"key":"$key","type":"text","label":"$key","validation":{"pattern":${JsonValue.String(pattern)}}}"""

    private fun form(
        fields: String,
        version: String = "",
    ): JsonValue = JsonValue.parse("""{"id":"patterns","title":"Patterns",$version"submit":{"label":"Send"},"fields":[$fields]}""")

    private fun form(patterns: List<String>): JsonValue =
        form(patterns.withIndex().joinToString(",") { (index, pattern) -> field("p$index", pattern) })

    private fun values(values: List<String>): JsonValue =
        JsonValue.parse(values.withIndex().joinToString(",", "{", "}") { (index, value) -> "\"p$index\":${JsonValue.String(value)}" })

    private fun keys(count: Int): List<String> = List(count) { "p$it" }

    private fun corpus(name: String): List<String> = corpus.map { it[name]?.stringValue.orEmpty() }

    @Test
    fun `runs every pattern of a corpus of real-world patterns`() {
        assertEquals(261, corpus.size)
        val definition = form(corpus("pattern"))
        assertEquals(emptyList<String>(), validateSubmission(definition, values(corpus("valid"))).errors.keys.toList())
        assertEquals(keys(corpus.size), validateSubmission(definition, values(corpus("invalid"))).errors.keys.toList())
    }

    @Test
    fun `runs a pattern that checkDefinition reports as slow`() {
        val patterns = listOf("^(a+)+$", "^(\\w+\\s?)+$", "^[A-Za-z]+(?:[ '-]?[A-Za-z]+)*$", "^\\d+\\d+$", "^(?:${"a".repeat(1000)})$")
        val definition = form(patterns)
        assertEquals(
            patterns.map {
                "slow-pattern warning"
            },
            checkDefinition(definition).map { "${it.code.rawValue} ${it.severity.name.lowercase()}" },
        )
        val valid = values(listOf("aaa", "Ada Lovelace", "Mary O'Neil", "12", "a".repeat(1000)))
        assertTrue(validateSubmission(definition, valid).errors.isEmpty())
        val invalid = values(listOf("aab", "Ada!", "Mary--Jane", "1", "a"))
        assertEquals(keys(patterns.size), validateSubmission(definition, invalid).errors.keys.toList())
    }

    @Test
    fun `runs a slow pattern inside a group`() {
        val group = """{"key":"people","type":"group","label":"People","fields":[${field("name", "^(\\w+\\s?)+$")}]}"""
        val definition = form(group, "\"formatVersion\":\"1.1\",")
        assertTrue(validateSubmission(definition, JsonValue.parse("""{"people":[{"name":"Ada Lovelace"}]}""")).errors.isEmpty())
        val rejected = validateSubmission(definition, JsonValue.parse("""{"people":[{"name":"Ada!"}]}"""))
        assertEquals(mapOf("people.0.name" to "Invalid format"), rejected.errors.toMap())
    }

    @Test
    fun `matches the browser engine on a slow pattern`() {
        val definition = form(listOf("^\\d+\\.?\\d*$"))
        val long = "1".repeat(1001)
        assertTrue(validateSubmission(definition, values(listOf(long))).errors.isEmpty())
        val engine = FormEngine(FormDefinition(definition), jsonObject("""{"p0":"$long"}"""), FormEngineOptions(onWarning = {}))
        assertTrue(engine.validate().isEmpty())
    }
}
