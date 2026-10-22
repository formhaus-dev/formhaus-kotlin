package dev.formhaus.compose.form

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.json
import dev.formhaus.compose.model.model
import dev.formhaus.compose.model.signupDefinition
import dev.formhaus.compose.model.submit
import dev.formhaus.compose.model.values
import dev.formhaus.core.i18n.localize
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val recalling =
    """
    {"id": "r", "title": "R", "formatVersion": "1.5", "submit": {"label": "Send"},
     "steps": [
       {"id": "about", "title": "About {{name}}", "description": "Hi {{name}}", "fields": [
         {"key": "name", "type": "text", "label": "Name"},
         {"key": "s", "type": "section", "title": "Section {{name}}", "description": "For {{name}}",
          "fields": [{"key": "email", "type": "email", "label": "Mail for {{name}}", "helperText": "Help {{name}}"}]},
         {"key": "team", "type": "group", "label": "Team of {{name}}", "helperText": "Add {{name}}", "itemLabel": "{label} {index}: {{member}}",
          "fields": [{"key": "member", "type": "text", "label": "Role of {{member}} for {{name}}"}]},
         {"key": "show", "type": "text", "label": "Only for Ada", "show": [{"field": "name", "eq": "Ada"}]},
         {"key": "hide", "type": "text", "label": "Only for Bob", "show": [{"field": "name", "eq": "Bob"}]}]},
       {"id": "next", "title": "Next", "fields": [{"key": "notes", "type": "text", "label": "Notes"}]}
     ]}
    """.trimIndent()

private const val VALUES = """{"name": "Ada", "team": [{"member": "Bob"}]}"""

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class RecallSlotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun slots(system: Boolean) {
        val form = model(recalling, VALUES)
        val progress = mutableListOf<String?>()
        val sections = mutableListOf<String>()
        val groups = mutableListOf<String>()
        val base = if (system) FormComponents.system else FormComponents.standard
        val components =
            base
                .progress { context ->
                    progress.clear()
                    progress += listOf(context.stepTitle, context.stepDescription) + context.steps.orEmpty().map { it.title }
                }.section { context ->
                    sections.clear()
                    sections += listOf(context.title, context.description.orEmpty())
                    context.content()
                }.group { context ->
                    groups.clear()
                    groups += listOf(context.label, context.helperText.orEmpty()) + context.items.map { it.title }
                    Column { context.items.forEach { it.content() } }
                }
        compose.setContent { Provided(components, FormBehavior(stepErrorMarks = true)) { FormhausFormContent(form) } }
        compose.waitForIdle()
        assertEquals(listOf("About Ada", "Hi Ada", "About Ada", "Next"), progress)
        assertEquals(listOf("Section Ada", "For Ada"), sections)
        assertEquals(listOf("Team of Ada", "Add Ada", "Team of Ada 1: Bob"), groups)
        compose.onNodeWithText("Help Ada").assertExists()
        compose.onNodeWithText("Only for Ada").assertExists()
        compose.onNodeWithText("Only for Bob").assertDoesNotExist()
    }

    @Test
    fun theFormhausSetShowsRecalledTextsAndConditions() = slots(system = false)

    @Test
    fun theSystemSetShowsRecalledTextsAndConditions() = slots(system = true)

    @Test
    fun translationsFromCoreRenderInLabelsErrorsAndTheSummary() {
        val overlay =
            json(
                """{"fields": {"email": {"label": "E-Mail"}}, "messages": {"required": "Pflichtfeld"},
                "labels": {"errorSummary": "Bitte prüfen"}}""",
            )
        val form = FormModel(localize(json(signupDefinition), overlay, "de"), values("""{"agree": true}""")) { _, _ -> null }
        compose.setContent { FormhausFormContent(form) }
        compose.onNodeWithText("E-Mail *").assertExists()
        compose.act(form) { submit() }
        compose.onNodeWithText("Bitte prüfen").assertExists()
        compose.onNodeWithText("Pflichtfeld").assertExists()
    }
}
