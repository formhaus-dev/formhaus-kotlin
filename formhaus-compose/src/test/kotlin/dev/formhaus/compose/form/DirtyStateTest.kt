package dev.formhaus.compose.form

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.formhaus.compose.model.model
import dev.formhaus.compose.model.setValue
import dev.formhaus.compose.model.values
import dev.formhaus.core.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val profile =
    """
    {"id": "profile", "title": "Profile", "submit": {"label": "Send"},
     "fields": [{"key": "name", "type": "text", "label": "Name"}]}
    """.trimIndent()

@RunWith(RobolectricTestRunner::class)
class DirtyStateTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun saveIsEnabledOnlyWhileTheFormHasUnsavedChanges() {
        val form = model(profile, """{"name": "Ada"}""")
        compose.setContent {
            Column {
                Button(onClick = form::markClean, enabled = form.isDirty) { Text("Save changes") }
                FormhausForm(form)
            }
        }
        val save = compose.onNodeWithText("Save changes")
        val name = compose.onNode(hasContentDescription("Name") and hasSetTextAction())
        save.assertIsNotEnabled()
        name.performTextInput("!")
        save.assertIsEnabled()
        assertEquals(listOf("name"), form.dirtyKeys)
        save.performClick()
        save.assertIsNotEnabled()
        name.performTextInput("?")
        save.assertIsEnabled()
    }

    @Test
    fun aModelThatNeverReadsItsDirtyStateComparesNoPayloads() {
        val form = model(profile, """{"name": "Ada"}""")
        compose.setContent { FormhausForm(form) }
        compose.onNode(hasContentDescription("Name") and hasSetTextAction()).performTextInput("!")
        compose.waitForIdle()
        assertFalse(form.watchesDirty)
    }

    @Test
    fun editsBeforeTheFirstReadCount() {
        val form = model(profile, """{"name": "Ada"}""")
        form.setValue("name", JsonValue.String("Grace"))
        assertTrue(form.isDirty)
        assertEquals(listOf("name"), form.dirtyKeys)
    }

    @Test
    fun aResetBeforeTheFirstReadIsTheBaseline() {
        val form = model(profile, """{"name": "Ada"}""")
        form.setValue("name", JsonValue.String("Grace"))
        form.engine.reset(values("""{"name": "Lin"}"""))
        assertFalse(form.isDirty)
        form.setValue("name", JsonValue.String("Ada"))
        assertEquals(listOf("name"), form.dirtyKeys)
    }
}
