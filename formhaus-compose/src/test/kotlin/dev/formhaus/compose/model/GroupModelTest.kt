package dev.formhaus.compose.model

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.renderer.FieldOption
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private val groupDefinition =
    """
    {"id": "team", "title": "Team", "submit": {"label": "Send"},
     "fields": [
       {"key": "country", "type": "select", "label": "Country", "options": [{"value": "de", "label": "Germany"}]},
       {"key": "city", "type": "select", "label": "City", "optionsFrom": "cities", "optionsDependsOn": ["country"]},
       {"key": "people", "type": "group", "label": "People", "defaultItems": 1, "validation": {"required": true},
        "fields": [{"key": "name", "type": "text", "label": "Name", "validation": {"required": true}}]}
     ]}
    """.trimIndent()

class GroupModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun itemFieldsHaveTheirOwnSignals() {
        val form = model(groupDefinition)
        val first = form.field("people.0.name")
        val group = form.group("people")
        assertEquals(1, group.items.size)
        assertTrue(fires({ first.value }) { form.setValue("people.0.name", JsonValue.String("Ada")) })
        assertFalse(fires({ group.items }) { form.setValue("people.0.name", JsonValue.String("Adam")) })
        assertTrue(fires({ group.items }) { form.addItem("people") })
        assertEquals(2, group.items.size)
        assertNull(form.field("people.1.name").value)
        assertEquals("people.1.name", form.focusRequest?.key)
        assertEquals("People 2", form.itemTitle(1, "people"))
    }

    @Test
    fun removingAnItemFocusesTheGroup() {
        val form = model(groupDefinition)
        form.addItem("people")
        assertTrue(form.removeItem(0, "people"))
        assertEquals(1, form.group("people").items.size)
        assertEquals("people", form.focusRequest?.key)
    }

    @Test
    fun itemErrorsCarryTheItemTitle() =
        runTest {
            val form = model(groupDefinition)
            form.submit()
            assertEquals(listOf("Name (People 1)"), form.errorGroups.flatMap { group -> group.entries.map { it.label } })
            assertEquals("people.0.name", form.focusRequest?.key)
        }

    @Test
    fun optionsProvidersLoadAndReloadWhenADependencyChanges() {
        val provider: OptionsProvider = { values ->
            if (values["country"] == JsonValue.String("de")) listOf(FieldOption("ber", "Berlin")) else emptyList()
        }
        val form = model(groupDefinition, options = FormRendererOptions(optionsProviders = mapOf("cities" to provider)))
        assertNull(form.options("city"))
        form.start()
        assertEquals(emptyList<Any>(), form.options("city"))
        form.setValue("country", JsonValue.String("de"))
        assertEquals(listOf("Berlin"), form.options("city")?.map { it.label })
    }

    @Test
    fun aFailingOptionsProviderReportsTheErrorAndFallsBackToTheStaticOptions() {
        val failure = IllegalStateException("offline")
        val reported = mutableListOf<Throwable>()
        val provider: OptionsProvider = { values ->
            check(values["country"] != JsonValue.String("fr")) { "offline" }
            if (values["country"] == JsonValue.String("de")) throw failure
            listOf(FieldOption("ber", "Berlin"))
        }
        val options = FormRendererOptions(optionsProviders = mapOf("cities" to provider), onError = { reported += it })
        val form = model(groupDefinition, options = options)
        form.start()
        assertEquals(listOf("Berlin"), form.options("city")?.map { it.label })
        form.setValue("country", JsonValue.String("de"))
        assertEquals(listOf<Throwable>(failure), reported)
        assertNull(form.options("city"))
        val unhandled = model(groupDefinition, options = FormRendererOptions(optionsProviders = mapOf("cities" to provider)))
        unhandled.start()
        unhandled.setValue("country", JsonValue.String("fr"))
        assertEquals("Form action failed", unhandled.actionError)
        assertNull(unhandled.options("city"))
    }
}
