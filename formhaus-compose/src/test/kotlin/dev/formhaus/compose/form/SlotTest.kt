package dev.formhaus.compose.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.FieldShell
import dev.formhaus.compose.containers.FormActionsBar
import dev.formhaus.compose.containers.StepProgressView
import dev.formhaus.compose.fields.RadioField
import dev.formhaus.compose.fields.TextAreaField
import dev.formhaus.compose.fields.TextInputField
import dev.formhaus.compose.model.model
import dev.formhaus.compose.theme.Palette
import dev.formhaus.core.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

private val slotted =
    """
    {"id": "s", "title": "S", "submit": {"label": "Send"},
     "steps": [
       {"id": "one", "title": "One", "fields": [
         {"key": "intro", "type": "section", "title": "Intro", "fields": [{"key": "a", "type": "text", "label": "A"}]},
         {"key": "g", "type": "group", "label": "G", "defaultItems": 2, "fields": [{"key": "n", "type": "text", "label": "N"}]}]},
       {"id": "two", "title": "Two", "fields": []}
     ]}
    """.trimIndent()

private val hostPrimary = Color(0xFF00CC00)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SlotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun slotComponents(log: CallLog): FormComponents =
        FormComponents.standard
            .section { context ->
                log.names += "section:${context.section.key}:${context.description.orEmpty()}"
                Column {
                    Text(context.title)
                    context.content()
                }
            }.group { context ->
                log.names += "group:${context.group.key}:${context.items.size}"
                Column {
                    Text(context.label)
                    context.items.forEach { item -> log.names += "item:${item.index}:${item.title}:${item.hasContent}" }
                    context.items.forEach { it.content() }
                }
            }.progress { context ->
                log.names += "progress:${context.current}/${context.total}:${context.counterLabel}:${context.steps?.size ?: 0}"
                StepProgressView(context)
            }.actions { context ->
                log.names += "actions:${context.state.primaryLabel}:${context.busy}"
                Text("Primary", Modifier.testTag("primary"))
                FormActionsBar(context)
            }

    @Test
    fun sectionsGroupsItemsProgressAndActionsUseRegisteredViews() {
        val form = model(slotted)
        val log = CallLog()
        compose.setContent {
            Provided(slotComponents(log), FormBehavior(stepErrorMarks = true)) { FormhausFormContent(form) }
        }
        compose.waitForIdle()
        assertTrue(log.names.toString(), "section:intro:" in log.names)
        assertTrue("group:g:2" in log.names)
        assertTrue("item:1:G 2:false" in log.names)
        assertTrue("progress:1/2:Step 1 of 2:2" in log.names)
        assertTrue("actions:Continue:false" in log.names)
        compose.onNodeWithTag("primary").assertExists()
    }

    @Test
    fun customTypesAndOverriddenBuiltInsRenderFromPublicBuildingBlocks() {
        val definition =
            """
            {"id": "c", "title": "C", "submit": {"label": "Send"}, "fields": [
              {"key": "tint", "type": "x-color", "label": "Tint", "validation": {"required": true}},
              {"key": "note", "type": "text", "label": "Note"}, {"key": "bio", "type": "textarea", "label": "Bio"}]}
            """.trimIndent()
        val form = model(definition, """{"tint": "#5502fb"}""")
        val rendered = mutableListOf<String>()
        val components =
            FormComponents.standard
                .field("x-color") { context ->
                    FieldShell(context) { Text(context.text) }
                }.field("text") { context ->
                    rendered += context.key
                    TextInputField(context)
                }.field("textarea") { TextAreaField(it) }
        compose.setContent { Provided(components) { FormhausForm(form) } }
        compose.onNodeWithText("#5502fb").assertExists()
        assertTrue("note" in rendered)
        assertEquals(JsonValue.String("#5502fb"), form.field("tint").value)
    }

    @Test
    fun aHeadlessRendererComposesOnlyTheFieldViewsItRegisters() {
        val types = listOf("radio", "select", "checkbox", "scale")
        val fields =
            types.joinToString {
                """{"key": "$it", "type": "$it", "label": "$it", "options": [{"value": "a", "label": "A"}]}"""
            }
        val form = model("""{"id": "all", "title": "All", "submit": {"label": "Send"}, "fields": [$fields]}""")
        val seen = mutableSetOf<String>()
        val components =
            FormComponents.headless.field("radio") {
                seen += "radio"
                RadioField(it)
            }
        compose.setContent { Provided(components) { FormFields(form) } }
        compose.waitForIdle()
        assertEquals(setOf("radio"), seen)
        compose.onNodeWithText("Unsupported field type: select").assertExists()
        compose.onNodeWithText("A").performClick()
        assertEquals(JsonValue.String("a"), form.field("radio").value)
    }

    private fun swatch(components: FormComponents): Int {
        val form = model("""{"id": "t", "title": "T", "submit": {"label": "Send"}, "fields": [{"key": "s", "type": "x-swatch"}]}""")
        val swatched =
            components.field("x-swatch") {
                Box(Modifier.size(24.dp).testTag("swatch").background(MaterialTheme.colorScheme.primary))
            }
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = hostPrimary)) { Provided(swatched) { FormhausForm(form) } }
        }
        return compose
            .onNodeWithTag("swatch")
            .captureToImage()
            .toPixelMap()[12, 12]
            .toArgb()
    }

    @Test
    fun customFieldsInTheSystemSetKeepTheHostTint() {
        assertEquals(hostPrimary.toArgb(), swatch(FormComponents.system))
    }

    @Test
    fun theFormhausSetTintsWithTheThemePrimary() {
        assertEquals(Palette.light.primary.toArgb(), swatch(FormComponents.standard))
    }
}
