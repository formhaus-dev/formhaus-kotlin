package dev.formhaus.core.engine

import dev.formhaus.core.definitionvalidation.IssueCode
import dev.formhaus.core.definitionvalidation.checkDefinition
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.support.definition
import dev.formhaus.research.Research
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OptionOrderTest {
    @Test
    fun `option warnings come in option order`() {
        val definition =
            JsonValue.parse(
                """{"formatVersion":"1.6","id":"f","title":"F","submit":{"label":"S"},"fields":[{"key":"plan","type":"radio","label":"Plan",
                "options":[{"value":"a","label":"A","pinned":true},{"value":"b","label":"B","image":"http://example.com/b.png"}]}]}""",
            )
        val codes = listOf(IssueCode.RANDOMIZE_NOT_APPLICABLE, IssueCode.UNSAFE_IMAGE_URL)
        val paths = checkDefinition(definition).filter { it.code in codes }.map { it.path }
        assertEquals(listOf("/fields/0/options/0/pinned", "/fields/0/options/1/image"), paths)
    }

    @Test
    fun `the engine takes its seed from the option, then from research, and keeps written order without research`() {
        val options = (0 until 8).joinToString(",") { """{"value":"p$it","label":"P"}""" }
        val form =
            definition(
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
