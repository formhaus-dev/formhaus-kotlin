package dev.formhaus.core.engine

import dev.formhaus.core.definitionvalidation.FormDefinitionError
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.renderer.RendererApi
import dev.formhaus.core.renderer.errorGroups
import dev.formhaus.core.support.engine
import dev.formhaus.core.support.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@OptIn(RendererApi::class)
class CoreAdditionsTest {
    @Test
    fun `replaceDefinition takes JSON and rejects an invalid definition`() {
        val engine =
            engine(
                """{"id":"f","submit":{"label":"S"},"fields":[{"key":"a","type":"text","label":"A"}]}""",
                """{"a":"x"}""",
            )
        engine.replaceDefinition(
            JsonValue.parse(
                """{"id":"f","submit":{"label":"S"},"fields":[
                    {"key":"a","type":"text","label":"A"},{"key":"b","type":"text","label":"B","defaultValue":"y"}]}""",
            ),
        )
        assertEquals(jsonObject("""{"a":"x","b":"y"}"""), engine.values)
        assertThrows<FormDefinitionError> { engine.replaceDefinition(JsonValue.parse("""{"id":"f"}""")) }
        assertEquals(jsonObject("""{"a":"x","b":"y"}"""), engine.values)
    }

    @Test
    fun `error groups follow the steps and join labels by punctuation`() {
        val engine =
            engine(
                """{"id":"f","submit":{"label":"S"},"steps":[
                {"id":"one","title":"Contact","fields":[{"key":"a","type":"text","label":"Name?"}]},
                {"id":"two","fields":[{"key":"b","type":"text","label":"City"},{"key":"c","type":"text","label":"Zip："}]}]}""",
            )
        engine.setErrors(errorsOf("""{"c":"Bad","a":"Taken","b":"Missing"}"""))
        val groups = engine.errorGroups(null)
        assertEquals(listOf("one", "two"), groups.map { it.id })
        assertEquals(listOf("Contact", "Step 2"), groups.map { it.title })
        assertEquals(listOf("Name? Taken", "City: Missing", "Zip： Bad"), groups.flatMap { it.entries }.map { it.text })
        assertEquals(listOf("a", "b", "c"), groups.flatMap { it.entries }.map { it.key })
    }
}
