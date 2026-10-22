package dev.formhaus.research

import dev.formhaus.core.definitionvalidation.IssueCode
import dev.formhaus.core.definitionvalidation.checkDefinition
import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.mutableStringMapOf
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.support.Fixtures
import dev.formhaus.core.support.definition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class OptionOrderTest {
    @TestFactory
    fun vectors(): List<DynamicTest> = Fixtures.conformanceNames.flatMap { name ->
        Fixtures.orders(name).mapIndexed { index, vector ->
            DynamicTest.dynamicTest("$name: $index") {
                val pinned = vector["pinned"]?.items.orEmpty().mapNotNull { it.number?.toInt() }.toSet()
                val count = vector["count"]?.number?.toInt() ?: 0
                val expected = vector["order"]?.items.orEmpty().mapNotNull { it.number?.toInt() }
                val seed = requireNotNull(vector["seed"]?.units)
                val key = requireNotNull(vector["key"]?.units)
                assertEquals(expected, optionOrder(seed, key, (0 until count).map { it in pinned }))
            }
        }
    }

    @Test
    fun `the vectors keep the code units of a lone surrogate seed`() {
        assertTrue(Fixtures.orders("option-order").any { it["seed"]?.units == "\uD800" })
    }

    @Test
    fun `option warnings come in option order`() {
        val definition = JsonValue.parse(
            """{"formatVersion":"1.6","id":"f","title":"F","submit":{"label":"S"},"fields":[{"key":"plan","type":"radio","label":"Plan",
                "options":[{"value":"a","label":"A","pinned":true},{"value":"b","label":"B","image":"http://example.com/b.png"}]}]}""",
        )
        val codes = listOf(IssueCode.RANDOMIZE_NOT_APPLICABLE, IssueCode.UNSAFE_IMAGE_URL)
        val paths = checkDefinition(definition).filter { it.code in codes }.map { it.path }
        assertEquals(listOf("/fields/0/options/0/pinned", "/fields/0/options/1/image"), paths)
    }

    @Test
    fun `the research order keeps pinned and exclusive options in place`() {
        val options = (0 until 6).map { index ->
            val json = mutableStringMapOf<JsonValue>("value" to JsonValue.String("o$index"), "label" to JsonValue.String("O"))
            if (index == 0) json["pinned"] = JsonValue.Bool(true)
            if (index == 5) json["exclusive"] = JsonValue.Bool(true)
            FieldOption(json)
        }
        val order = Research().order("seed", "plan", options).mapNotNull { it.value }
        val pinned = listOf(true, false, false, false, false, true)
        assertEquals(optionOrder("seed", "plan", pinned).map { "o$it" }, order)
        assertEquals("o0", order.first())
        assertEquals("o5", order.last())
        assertEquals(32, Research().seed().length)
        assertNotEquals(Research().seed(), Research().seed())
    }

    @Test
    fun `the engine takes its seed from the option, then from research, and keeps written order without research`() {
        val options = (0 until 8).joinToString(",") { """{"value":"p$it","label":"P"}""" }
        val form = definition(
            """{"formatVersion":"1.6","id":"f","submit":{"label":"S"},
               "fields":[{"key":"plan","type":"radio","label":"Plan","randomize":true,"options":[$options]}]}""",
        )
        val written = (0 until 8).map { "p$it" }
        val plain = FormEngine(form, options = FormEngineOptions(onWarning = {}, seed = "fixed"))
        assertEquals("fixed", plain.seed)
        assertEquals(written, plain.orderOptions("plan").mapNotNull { it.value })
        val seeded = FormEngine(form, options = FormEngineOptions(seed = "fixed", research = Research()))
        val again = FormEngine(form, options = FormEngineOptions(seed = "fixed", research = Research()))
        assertEquals(again.orderOptions("plan").map { it.value }, seeded.orderOptions("plan").map { it.value })
        assertEquals(written.toSet(), seeded.orderOptions("plan").mapNotNull { it.value }.toSet())
        assertEquals(32, FormEngine(form, options = FormEngineOptions(seed = "", research = Research())).seed.length)
    }
}
