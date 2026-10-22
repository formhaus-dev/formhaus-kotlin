package dev.formhaus.compose.model

import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.research.Research
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private val survey =
    """
    {"formatVersion": "1.6", "id": "s", "title": "S", "submit": {"label": "Send"},
     "fields": [
       {"key": "tools", "type": "multiselect", "label": "Tools", "randomize": true,
        "options": [{"value": "figma", "label": "Figma"}, {"value": "sketch", "label": "Sketch"},
          {"value": "framer", "label": "Framer"}, {"value": "penpot", "label": "Penpot"},
          {"value": "other", "label": "Other", "pinned": true}, {"value": "none", "label": "None of these", "exclusive": true}]},
       {"key": "team", "type": "group", "label": "Team", "defaultItems": 1,
        "fields": [{"key": "role", "type": "radio", "label": "Role", "randomize": true,
          "options": [{"value": "design", "label": "Design"}, {"value": "engineering", "label": "Engineering"},
            {"value": "product", "label": "Product"}, {"value": "research", "label": "Research"}]}]}
     ]}
    """.trimIndent()

private fun FormModel.shown(
    field: FormField,
    key: String = field.key,
): List<String?> = engine.orderOptions(key, options(key)).map { it.value }

class OptionOrderTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun fieldsShowOptionsInTheOrderOfTheEngine() {
        val options = FormEngineOptions(seed = "visitor-1", research = Research())
        val form = FormModel(FormDefinition(json(survey)), engineOptions = options) { _, _ -> null }
        assertEquals(listOf("penpot", "framer", "sketch", "figma", "other", "none"), form.shown(form.structure.visibleFields[0]))
        val role =
            form.structure.visibleFields[1]
                .items
                .first()
        assertEquals(listOf("product", "research", "engineering", "design"), form.shown(role, "team.0.role"))
    }

    @Test
    fun withoutResearchTheOptionsKeepTheirWrittenOrder() {
        val options = FormEngineOptions(seed = "visitor-1", onWarning = {})
        val form = FormModel(FormDefinition(json(survey)), engineOptions = options) { _, _ -> null }
        assertEquals(listOf("figma", "sketch", "framer", "penpot", "other", "none"), form.shown(form.structure.visibleFields[0]))
    }
}
