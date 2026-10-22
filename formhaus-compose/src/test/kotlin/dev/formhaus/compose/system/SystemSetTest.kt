package dev.formhaus.compose.system

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.formhaus.compose.form.CallLog
import dev.formhaus.compose.form.FormBehavior
import dev.formhaus.compose.form.FormComponents
import dev.formhaus.compose.form.FormhausFormContent
import dev.formhaus.compose.form.Provided
import dev.formhaus.compose.model.FormRendererOptions
import dev.formhaus.compose.model.json
import dev.formhaus.compose.model.model
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.renderer.LabelSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val composed =
    """
    {"id": "c", "title": "C", "submit": {"label": "Send"}, "fields": [
      {"key": "team", "type": "section", "title": "Team", "fields": [
        {"key": "lead", "type": "text", "label": "Lead"},
        {"key": "people", "type": "group", "label": "People", "defaultItems": 1, "validation": {"minLength": 1},
         "fields": [{"key": "name", "type": "text", "label": "Name", "validation": {"required": true}}]}]}]}
    """.trimIndent()

val survey =
    """
    {"id": "start", "title": "Start a project", "submit": {"label": "Send request"},
     "steps": [
       {"id": "scope", "title": "Scope", "description": "Tell us what you need", "fields": [
         {"key": "name", "type": "text", "label": "Your name", "placeholder": "Grace Hopper", "helperText": "As it appears on invoices"},
         {"key": "services", "type": "multiselect", "label": "Services", "validation": {"required": true},
          "options": [{"value": "d", "label": "Design"}, {"value": "v", "label": "Development"}]},
         {"key": "budget", "type": "radio", "label": "Budget",
          "options": [{"value": "s", "label": "Under ten thousand"}, {"value": "l", "label": "Above ten thousand"}]},
         {"key": "ease", "type": "scale", "label": "How sure are you",
          "options": [{"value": "1", "label": "Exploring"}, {"value": "5", "label": "Decided"}]},
         {"key": "brief", "type": "textarea", "label": "Project brief", "placeholder": "Goals and deadlines"}]},
       {"id": "contact", "title": "Contact", "fields": [{"key": "email", "type": "email", "label": "Email"}]}
     ]}
    """.trimIndent()

private val ordered =
    listOf(
        "Step 1 of 2",
        "Scope",
        "Tell us what you need",
        "Your name",
        "Services",
        "Design",
        "Development",
        "Budget",
        "Under ten thousand",
        "Above ten thousand",
        "How sure are you",
        "Exploring",
        "Decided",
        "Project brief",
        "Continue",
    )

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class SystemSetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aGroupInsideASectionRendersItsItemsAsFields() {
        val log = CallLog()
        val form = model(composed)
        val components =
            FormComponents.system.field("text") { context ->
                log.names += context.key
                Text(context.label)
            }
        compose.setContent { Provided(components) { FormhausFormContent(form) } }
        compose.waitForIdle()
        assertTrue("lead" in log.names)
        assertTrue("people.0.name" in log.names)
    }

    @Test
    fun registeredSectionAndGroupBuildersReplaceTheNativeContainers() {
        val log = CallLog()
        val components =
            FormComponents.system
                .section { context ->
                    log.names += "section:${context.section.key}"
                    Column { context.content() }
                }.group { context ->
                    log.names += "group:${context.group.key}"
                    Text(context.label)
                }
        val form = model(composed)
        compose.setContent { Provided(components) { FormhausFormContent(form) } }
        compose.waitForIdle()
        assertEquals(setOf("section:team", "group:people"), log.names)
    }

    @Test
    fun theChoicePromptComesFromTheLabelCatalog() {
        val form = model(composed)
        assertEquals("Select", form.label("select"))
        val german =
            model(
                composed,
                options = FormRendererOptions(labels = LabelSource.Catalog(json("""{"select": "Auswählen"}"""))),
            )
        assertEquals("Auswählen", german.label("select"))
    }

    private fun lines(): List<String> {
        val nodes = mutableListOf<SemanticsNode>()

        fun walk(node: SemanticsNode) {
            nodes += node
            node.children.forEach(::walk)
        }
        walk(compose.onNode(isRoot()).fetchSemanticsNode())
        return nodes
            .flatMap { node ->
                val texts =
                    node.config
                        .getOrNull(SemanticsProperties.Text)
                        .orEmpty()
                        .map { it.text } +
                        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                texts.map { Triple(node.boundsInRoot.top, node.boundsInRoot.left, it) }
            }.sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.third }
    }

    private fun sameTexts(components: FormComponents) {
        val form = model(survey)
        compose.setContent { Provided(components) { FormhausFormContent(form) } }
        compose.waitForIdle()
        val lines = lines()
        val positions = ordered.map { text -> lines.indexOfFirst { text in it } }
        assertTrue("missing in $lines", positions.none { it < 0 })
        assertEquals("out of order: $lines", positions.sorted(), positions)
        assertTrue(lines.any { "As it appears on invoices" in it })
    }

    @Test
    fun theFormhausSetShowsEveryTextOfTheStepInOrder() = sameTexts(FormComponents.standard)

    @Test
    fun theSystemSetShowsEveryTextOfTheStepInOrder() = sameTexts(FormComponents.system)

    @Test
    fun systemControlsWriteValues() {
        val definition =
            """
            {"id": "f", "title": "F", "submit": {"label": "Save"}, "fields": [
              {"key": "name", "type": "text", "label": "Name"},
              {"key": "seat", "type": "radio", "label": "Seat", "options": [{"value": "a", "label": "Aisle"}, {"value": "w", "label": "Window"}]},
              {"key": "plan", "type": "select", "label": "Plan", "options": [{"value": "free", "label": "Free"}, {"value": "pro", "label": "Pro"}]},
              {"key": "news", "type": "switch", "label": "News"},
              {"key": "agree", "type": "checkbox", "label": "Agree"}]}
            """.trimIndent()
        val form = model(definition)
        compose.setContent { Provided(FormComponents.system, FormBehavior()) { FormhausFormContent(form) } }
        compose.onNode(hasContentDescription("Name") and hasSetTextAction()).performTextInput("Ada")
        compose.onNodeWithText("Window").performClick()
        compose.onNodeWithContentDescription("News").performClick()
        compose.onNodeWithContentDescription("Agree").performClick()
        compose.onNodeWithContentDescription("Plan").performClick()
        compose.onNodeWithText("Pro").performClick()
        assertEquals(JsonValue.String("Ada"), form.field("name").value)
        assertEquals(JsonValue.String("w"), form.field("seat").value)
        assertEquals(JsonValue.Bool(true), form.field("news").value)
        assertEquals(JsonValue.Bool(true), form.field("agree").value)
        assertEquals(JsonValue.String("pro"), form.field("plan").value)
    }
}
