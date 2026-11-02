package dev.formhaus.compose.fields

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.formhaus.compose.form.FormhausForm
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.FormRendererOptions
import dev.formhaus.compose.model.addItem
import dev.formhaus.compose.model.model
import dev.formhaus.compose.model.setValue
import dev.formhaus.core.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val workspace =
    """
    {"formatVersion": "1.8", "id": "w", "title": "W", "submit": {"label": "Save"}, "fields": [
      {"key": "seats", "type": "number", "label": "Seats"},
      {"key": "account", "type": "text", "label": "Account ID", "readOnly": true},
      {"key": "plan", "type": "radio", "label": "Plan", "readOnly": [{"field": "seats", "gt": 100}], "options": [
        {"value": "starter", "label": "Starter", "description": "Up to 5 seats", "disabled": [{"field": "seats", "gt": 5}]},
        {"value": "pro", "label": "Pro"}]},
      {"key": "tasks", "type": "group", "label": "Tasks", "reorderable": true, "itemLabel": "Task {index}", "fields": [
        {"key": "title", "type": "text", "label": "Title"}]}]}
    """.trimIndent()

private val team =
    """
    {"formatVersion": "1.9", "id": "t", "title": "T", "submit": {"label": "Save"}, "fields": [
      {"key": "members", "type": "group", "label": "Members", "itemLabel": "Member {index}", "fields": [
        {"key": "id", "type": "hidden", "label": "Member id", "defaultValue": "new"},
        {"key": "name", "type": "text", "label": "Name"}]}]}
    """.trimIndent()

private val readOnlyText = SemanticsMatcher.expectValue(SemanticsProperties.IsEditable, false)

private val focusable = SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused)

private val hasNoClick = SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick)

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class OptionRulesTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(
        values: String,
        options: FormRendererOptions = FormRendererOptions(),
        definition: String = workspace,
    ): FormModel {
        val form = model(definition, values, options)
        compose.setContent { Themed { FormhausForm(form) } }
        compose.waitForIdle()
        return form
    }

    @Test
    fun aDisabledOptionFollowsItsConditionsAndKeepsItsDescription() {
        val form = render("""{"seats": 8}""")
        val starter = compose.onNode(hasText("Starter") and hasText("Up to 5 seats"), useUnmergedTree = false)
        starter.assertIsNotEnabled()
        compose.runOnIdle { form.setValue("seats", JsonValue.Number(3.0)) }
        starter.assertIsEnabled().performClick()
        assertEquals(JsonValue.String("starter"), form.engine.values["plan"])
    }

    @Test
    fun aReadOnlyFieldStaysFocusableAndIgnoresInput() {
        val form = render("""{"seats": 200, "account": "acct-1", "plan": "pro"}""")
        compose.onNode(hasContentDescription("Account ID") and readOnlyText).assert(focusable)
        compose.runOnIdle { form.setValue("account", JsonValue.String("x")) }
        assertEquals(JsonValue.String("x"), form.engine.values["account"])
        val starter = compose.onNode(hasText("Starter") and hasText("Up to 5 seats"))
        starter.assert(hasNoClick).assertIsEnabled()
        starter.assert(focusable)
        compose
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Read only"))
            .assertExists()
    }

    @Test
    fun moveButtonsMoveTheItemAnnounceItAndKeepFocus() {
        val form = render("""{"tasks": [{"title": "A"}, {"title": "B"}]}""")
        compose.onNodeWithContentDescription("Move up, Task 1").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move down, Task 2").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move down, Task 1").performClick()
        compose.waitForIdle()
        assertEquals(JsonValue.parse("""[{"title": "B"}, {"title": "A"}]"""), form.engine.values["tasks"])
        assertEquals("Task 1 moved to position 2", form.alerts.announcement?.text)
        compose.onNodeWithContentDescription("Move up, Task 2").assertIsFocused()
    }

    @Test
    fun theReadOnlyOptionLocksEveryFieldAndHidesItemButtons() {
        assertEquals(FormRendererOptions(readOnly = true), FormRendererOptions().copy(readOnly = true))
        assertEquals(true, FormRendererOptions(readOnly = true).copy(lang = "de").readOnly)
        val form = render("""{"seats": 2, "tasks": [{"title": "A"}, {"title": "B"}]}""", FormRendererOptions(readOnly = true))
        compose.onNode(hasContentDescription("Seats") and readOnlyText).assert(focusable)
        compose.onNode(hasText("Starter") and hasText("Up to 5 seats")).assert(hasNoClick)
        compose.onAllNodesWithText("Add").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Move down, Task 1").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Remove Task 1").assertCountEquals(0)
        compose.runOnIdle { form.updateHandlers(options = FormRendererOptions()) }
        compose.onNodeWithContentDescription("Move down, Task 1").assertExists()
        compose.onNodeWithContentDescription("Remove Task 1").assertExists()
        compose.onNodeWithText("Add").assertExists()
    }

    @Test
    fun aHiddenItemFieldRendersNothingAndKeepsItsValue() {
        val form = render("""{"members": [{"id": "m-1", "name": "Ada"}]}""", definition = team)
        compose.onAllNodesWithContentDescription("Member id").assertCountEquals(0)
        compose.onAllNodesWithText("m-1").assertCountEquals(0)
        compose.runOnIdle { form.addItem("members") }
        assertEquals(
            JsonValue.parse("""[{"id": "m-1", "name": "Ada"}, {"id": "new"}]"""),
            form.engine.values["members"],
        )
    }
}
