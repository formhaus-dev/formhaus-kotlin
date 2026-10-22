package dev.formhaus.compose.model

import dev.formhaus.core.renderer.FieldOption
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

private fun single(field: String): String = """{"id": "f", "title": "T", "submit": {"label": "Send"}, "fields": [$field]}"""

private const val CITY_SELECT = """{"key": "city", "type": "select", "label": "City", "optionsFrom": "%s"}"""

class ReplaceDefinitionTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun aFieldOfANewTypeAtTheSamePositionChangesTheStructure() {
        val form = model(single("""{"key": "age", "type": "text", "label": "Age"}"""))
        val before = form.structureVersion
        form.engine.replaceDefinition(json(single("""{"key": "age", "type": "number", "label": "Age"}""")))
        assertEquals(before + 1, form.structureVersion)
        assertEquals(listOf("number"), form.structure.visibleFields.map { it.type })
    }

    @Test
    fun aNewOptionsProviderReplacesTheOldOneAndDropsItsPendingResponse() {
        val gate = CompletableDeferred<Unit>()
        val log = mutableListOf<String>()
        val providers: Map<String, OptionsProvider> =
            mapOf(
                "citiesA" to {
                    log += "a:started"
                    gate.await()
                    log += "a:returned"
                    listOf(FieldOption("a", "Amsterdam"))
                },
                "citiesB" to { listOf(FieldOption("b", "Berlin")) },
            )
        val form = model(single(CITY_SELECT.format("citiesA")), options = FormRendererOptions(optionsProviders = providers))
        form.start()
        assertEquals(listOf("a:started"), log)
        form.engine.replaceDefinition(json(single(CITY_SELECT.format("citiesB"))))
        assertEquals(listOf("Berlin"), form.options("city")?.map { it.label })
        gate.complete(Unit)
        assertEquals("a:returned", log.last())
        assertEquals(listOf("Berlin"), form.options("city")?.map { it.label })
    }

    @Test
    fun aFieldWithoutAProviderAfterReplacementDropsTheLoadedOptions() {
        val providers: Map<String, OptionsProvider> = mapOf("cities" to { listOf(FieldOption("b", "Berlin")) })
        val form = model(single(CITY_SELECT.format("cities")), options = FormRendererOptions(optionsProviders = providers))
        form.start()
        assertEquals(listOf("Berlin"), form.options("city")?.map { it.label })
        form.engine.replaceDefinition(
            json(single("""{"key": "city", "type": "select", "label": "City", "options": [{"value": "r", "label": "Rome"}]}""")),
        )
        assertNull(form.options("city"))
    }
}
