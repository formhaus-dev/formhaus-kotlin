package dev.formhaus.compose.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.form.FormBehavior
import dev.formhaus.compose.form.LocalFormBehavior
import dev.formhaus.compose.model.FormRendererOptions
import dev.formhaus.compose.model.TestIds
import dev.formhaus.compose.model.focus
import dev.formhaus.compose.model.model
import dev.formhaus.compose.model.next
import dev.formhaus.compose.model.setErrors
import dev.formhaus.compose.theme.Palette
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

private val pair =
    """
    {"id": "p", "title": "P", "submit": {"label": "Send"},
     "steps": [
       {"id": "one", "title": "One", "fields": [
         {"key": "a", "type": "text", "label": "A", "validation": {"required": true}},
         {"key": "b", "type": "text", "label": "B", "validation": {"required": true}}]},
       {"id": "two", "title": "Two", "fields": [{"key": "c", "type": "text", "label": "C"}]}
     ]}
    """.trimIndent()

private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FieldBehaviorTest {
    @get:Rule
    val compose = createComposeRule()

    private fun focusedKeys(form: dev.formhaus.compose.model.FormModel): List<String> =
        form.structure.visibleFields
            .map { it.key }
            .filter { form.field(it).hasFocus }

    private fun renderPair(
        values: String = "{}",
        summary: Boolean = true,
    ): dev.formhaus.compose.model.FormModel {
        val form = model(pair, values)
        compose.setContent {
            CompositionLocalProvider(LocalFormBehavior provides FormBehavior(errorSummary = summary)) { Themed { FieldList(form) } }
        }
        compose.waitForIdle()
        return form
    }

    @Test
    fun twoOrMoreErrorsLeaveFocusForTheSummary() {
        val form = renderPair()
        compose.runOnIdle { form.scope.launch { form.next() } }
        compose.waitForIdle()
        assertEquals(true, form.focusRequest?.summary)
        assertEquals(emptyList<String>(), focusedKeys(form))
    }

    @Test
    fun oneErrorMovesFocusToItsField() {
        val form = renderPair("""{"a": "x"}""")
        compose.runOnIdle { form.scope.launch { form.next() } }
        compose.waitForIdle()
        assertEquals(false, form.focusRequest?.summary)
        assertEquals(listOf("b"), focusedKeys(form))
    }

    @Test
    fun withTheSummaryOffFocusMovesToTheFirstInvalidField() {
        val form = renderPair(summary = false)
        compose.runOnIdle { form.scope.launch { form.next() } }
        compose.waitForIdle()
        assertEquals(listOf("a"), focusedKeys(form))
    }

    @Test
    fun serverErrorsFollowTheSameRuleAndFocusReachesAField() {
        val form = renderPair("""{"a": "x", "b": "y"}""")
        compose.runOnIdle { form.setErrors(stringMapOf("a" to "Taken", "b" to "Taken")) }
        compose.waitForIdle()
        assertEquals(true, form.focusRequest?.summary)
        assertEquals(emptyList<String>(), focusedKeys(form))
        compose.runOnIdle { form.focus("b") }
        compose.waitForIdle()
        assertEquals(listOf("b"), focusedKeys(form))
    }

    @Test
    fun aFocusRequestWaitsUntilTheFieldIsUnlocked() {
        val form = renderPair()
        compose.runOnIdle {
            form.loading = true
            form.focus("b")
        }
        compose.waitForIdle()
        assertEquals(emptyList<String>(), focusedKeys(form))
        compose.runOnIdle { form.loading = false }
        compose.waitForIdle()
        assertEquals(listOf("b"), focusedKeys(form))
    }

    @Test
    fun controlsCarryRolesNamesStatesAndTestIds() {
        val form =
            model(
                definition(
                    """{"key": "plan", "type": "radio", "label": "Plan", "validation": {"required": true},
                        "options": [{"value": "free", "label": "Free"}, {"value": "pro", "label": "Pro"}]}""",
                    """{"key": "agree", "type": "checkbox", "label": "Agree"}""",
                    """{"key": "news", "type": "switch", "label": "News"}""",
                    """{"key": "stars", "type": "rating", "label": "Stars"}""",
                    """{"key": "name", "type": "text", "label": "Name"}""",
                ),
                options = FormRendererOptions(testIds = TestIds.Automatic),
            )
        compose.setContent { Themed { FieldList(form) } }
        compose.onNodeWithTag("f.plan.pro").assert(hasRole(Role.RadioButton)).performClick()
        compose.onNodeWithTag("f.plan.pro").assertIsSelected()
        assertEquals(JsonValue.String("pro"), form.field("plan").value)
        compose.onNodeWithContentDescription("Agree").assert(hasRole(Role.Checkbox)).performClick()
        assertEquals(JsonValue.Bool(true), form.field("agree").value)
        compose.onNodeWithContentDescription("News").assert(hasRole(Role.Switch)).performClick()
        assertEquals(JsonValue.Bool(true), form.field("news").value)
        compose.onNodeWithTag("f.stars.3").assert(hasRole(Role.RadioButton)).performClick()
        assertEquals(JsonValue.Number(3.0), form.field("stars").value)
        compose.onNode(hasContentDescription("Name") and hasSetTextAction()).performTextInput("Ada")
        assertEquals(JsonValue.String("Ada"), form.field("name").value)
        compose.onNodeWithTag("f.name").assertExists()
        compose.runOnIdle { form.setErrors(stringMapOf("plan" to "Pick one")) }
        val error =
            compose
                .onNodeWithTag("f.plan")
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Error)
        assertEquals("Pick one", error)
    }

    @Test
    fun numberFieldsKeepTheDraftAndStoreNumbers() {
        val form = model(definition("""{"key": "age", "type": "number", "label": "Age"}"""))
        compose.setContent { Themed { FieldList(form) } }
        compose.onNode(hasContentDescription("Age") and hasSetTextAction()).performTextInput("4,5")
        assertEquals(JsonValue.Number(4.5), form.field("age").value)
        compose.onNode(hasContentDescription("Age") and hasSetTextAction()).assert(
            SemanticsMatcher("draft") { node ->
                node.config.getOrNull(SemanticsProperties.EditableText)?.text == "4,5"
            },
        )
    }

    @Test
    fun theFormhausSetTintsMaterialWithTheThemePrimary() {
        compose.setContent { Themed { Box(Modifier.size(8.dp).testTag("swatch").background(MaterialTheme.colorScheme.primary)) } }
        val pixel = compose.onNodeWithTag("swatch").captureToImage().toPixelMap()[4, 4]
        assertEquals(Palette.light.primary.toArgb(), pixel.toArgb())
    }

    @Test
    fun numbersParseLikeTheSwiftRenderer() {
        assertEquals(2.5, parseNumber(" 2,5 "))
        assertEquals(null, parseNumber("1d"))
        assertEquals(null, parseNumber("NaN"))
        assertEquals(null, parseNumber("1e400"))
        assertEquals(JsonValue.Null, numberValue(""))
        assertEquals(JsonValue.String("12a"), numberValue("12a"))
        assertTrue(numberValue("-3") == JsonValue.Number(-3.0))
    }
}
