package dev.formhaus.compose.containers

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.formhaus.compose.form.FormComponents
import dev.formhaus.compose.form.FormhausFormContent
import dev.formhaus.compose.form.Provided
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.model
import dev.formhaus.compose.model.setValue
import dev.formhaus.core.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val team =
    """
    {"id": "g", "title": "G", "submit": {"label": "Send"},
     "fields": [{"key": "team", "type": "group", "label": "Team", "defaultItems": 2, "validation": {"minLength": 1, "maxLength": 3},
                 "fields": [{"key": "name", "type": "text", "label": "Name"}]}]}
    """.trimIndent()

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h1400dp")
class GroupContainerTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(components: FormComponents = FormComponents.standard): FormModel {
        val form = model(team)
        compose.setContent { Provided(components) { FormhausFormContent(form) } }
        compose.waitForIdle()
        return form
    }

    @Test
    fun groupItemsReportContentAsItIsTypedAndRemoveThroughTheCustomDialog() {
        var hasContent = emptyList<Boolean>()
        var requests = emptyList<() -> Unit>()
        val components =
            FormComponents.standard
                .group { context ->
                    hasContent = context.items.map { it.hasContent }
                    requests = context.items.map { it.requestRemove }
                    Column { context.items.forEach { it.content() } }
                }.confirmDialog { context -> TextButton(context.confirm) { Text(context.confirmLabel) } }
        val form = render(components)
        assertEquals(listOf(false, false), hasContent)
        compose.runOnIdle { form.setValue("team.1.name", JsonValue.String("Ada")) }
        compose.waitForIdle()
        assertEquals(listOf(false, true), hasContent)
        compose.runOnIdle { requests.first()() }
        compose.waitForIdle()
        assertEquals(1, form.group("team").items.size)
    }

    @Test
    fun theDefaultGroupAddsUpToItsMaximumAndRemovesDownToItsMinimum() {
        val form = render()
        compose.onNodeWithText("Add").performClick()
        assertEquals(3, form.group("team").items.size)
        compose.onNodeWithText("Add").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove Team 3").performClick()
        compose.onNodeWithContentDescription("Remove Team 2").performClick()
        assertEquals(1, form.group("team").items.size)
        compose.onNodeWithText("Remove").assertDoesNotExist()
        compose.onNodeWithText("Add").assertExists()
    }

    @Test
    fun removingAnItemWithContentAsksFirst() {
        val form = render()
        compose.runOnIdle { form.setValue("team.1.name", JsonValue.String("Ada")) }
        compose.onNodeWithContentDescription("Remove Team 2").performClick()
        assertEquals(2, form.group("team").items.size)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(2, form.group("team").items.size)
        compose.onNodeWithContentDescription("Remove Team 2").performClick()
        compose.onNodeWithText("Remove Team 2").performClick()
        assertEquals(1, form.group("team").items.size)
        assertTrue(form.group("team").hasContent.none { it })
    }
}
