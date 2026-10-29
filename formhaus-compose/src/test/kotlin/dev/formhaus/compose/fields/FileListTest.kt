package dev.formhaus.compose.fields

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.model
import dev.formhaus.core.json.JsonValue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

private fun papers(version: String) =
    """{"id": "f", "title": "F", "formatVersion": "$version", "submit": {"label": "Send"}, "fields": [
       {"key": "docs", "type": "file", "label": "Documents", "multiple": true}]}"""

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h1400dp")
class FileListTest {
    @get:Rule
    val compose = createComposeRule()

    private val cache = ApplicationProvider.getApplicationContext<Context>().cacheDir

    private fun upload(
        form: FormModel,
        name: String,
    ): File {
        val source = File.createTempFile("formhaus-", ".txt").apply { writeText(name) }
        return requireNotNull(runBlocking { UploadStore.copy(cache, form.uploadOwner, { name }) { source.inputStream() } }).also {
            source.delete()
        }
    }

    private fun render(version: String = "1.7"): FormModel {
        val form = model(papers(version))
        compose.setContent { Themed { FieldList(form) } }
        compose.waitForIdle()
        return form
    }

    private fun context(form: FormModel) = FieldContext(form.structure.visibleFields.first(), form)

    private fun announced(form: FormModel): String? = form.alerts.announcement?.text

    @Test
    fun pickedFilesAddToTheListAndEachHasANamedRemoveButton() {
        val form = render()
        val files = listOf(upload(form, "a.pdf"), upload(form, "b.pdf"), upload(form, "c.pdf"))
        compose.runOnIdle { context(form).addFiles(files.take(2)) }
        compose.runOnIdle { context(form).addFiles(files.drop(2)) }
        assertEquals("c.pdf added", announced(form))
        assertEquals(files, form.pickedFiles("docs"))
        assertEquals(files.map { JsonValue.String(fileValue(it)) }, form.engine.values["docs"]?.arrayValue)
        for (name in listOf("a.pdf", "b.pdf", "c.pdf")) {
            compose.onNodeWithText(name).assertExists()
            compose.onNodeWithContentDescription("Remove $name").assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithContentDescription("Remove b.pdf").performClick()
        compose.waitForIdle()
        assertEquals("b.pdf removed", announced(form))
        assertFalse(files[1].exists())
        assertEquals(listOf(files[0], files[2]), form.pickedFiles("docs"))
        compose.onNodeWithContentDescription("Remove c.pdf").assertIsFocused()
        compose.onNodeWithContentDescription("Remove c.pdf").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Remove a.pdf").assertIsFocused()
        compose.onNodeWithContentDescription("Remove a.pdf").performClick()
        compose.waitForIdle()
        assertEquals(JsonValue.Array(emptyList()), form.engine.values["docs"])
        compose.onNode(focused).assert(hasContentDescription("Documents"))
        form.close()
    }

    @Test
    fun appReferencesStayInTheListAndAreNotShown() {
        val form = render()
        val file = upload(form, "a.pdf")
        compose.runOnIdle { form.engine.setValue("docs", JsonValue.Array(listOf(JsonValue.String("upload-1")))) }
        compose.runOnIdle { context(form).addFiles(listOf(file)) }
        compose.onNodeWithText("upload-1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove a.pdf").performClick()
        compose.waitForIdle()
        assertEquals(JsonValue.Array(listOf(JsonValue.String("upload-1"))), form.engine.values["docs"])
        form.close()
    }

    @Test
    fun belowFormat17TheFieldKeepsOneFile() {
        val form = render("1.6")
        val file = upload(form, "a.pdf")
        assertFalse(
            form.structure.visibleFields
                .first()
                .isFileList,
        )
        compose.runOnIdle { context(form).replaceFile(file) }
        compose.onNodeWithText("Remove").assertDoesNotExist()
        assertEquals(fileValue(file), form.engine.values["docs"]?.stringValue)
        assertEquals(listOf(file), form.pickedFiles("docs"))
        form.close()
    }
}

private val focused = SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)
