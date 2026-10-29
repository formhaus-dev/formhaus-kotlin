package dev.formhaus.compose.fields

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.model
import dev.formhaus.compose.model.setValue
import dev.formhaus.core.json.JsonValue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

private const val RECALLING =
    """{"id": "f", "title": "F", "formatVersion": "1.5", "submit": {"label": "Send"}, "fields": [
       {"key": "cv", "type": "file", "label": "CV"},
       {"key": "note", "type": "text", "label": "Note", "helperText": "You sent {{cv}}"}]}"""

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h1400dp")
class FileRecallTest {
    @get:Rule
    val compose = createComposeRule()

    private val cache = ApplicationProvider.getApplicationContext<Context>().cacheDir

    private fun upload(
        form: FormModel,
        name: String,
    ): File {
        val source = File.createTempFile("formhaus-", ".pdf").apply { writeText(name) }
        return requireNotNull(runBlocking { UploadStore.copy(cache, form.uploadOwner, { name }) { source.inputStream() } }).also {
            source.delete()
        }
    }

    @Test
    fun aPickedFileRecallsItsNameAndAnAppReferenceStaysAsWritten() {
        val form = model(RECALLING)
        compose.setContent { Themed { FieldList(form) } }
        val file = upload(form, "ada-cv.pdf")
        compose.runOnIdle { form.setValue("cv", JsonValue.String(fileValue(file))) }
        compose.onNodeWithText("You sent ada-cv.pdf").assertExists()
        compose.runOnIdle { form.setValue("cv", JsonValue.String("upload-123")) }
        compose.onNodeWithText("You sent upload-123").assertExists()
        form.close()
    }

    @Test
    fun aListOfPickedFilesRecallsTheirNames() {
        val form = model(RECALLING)
        val files = listOf(upload(form, "a.pdf"), upload(form, "b.pdf"))
        form.setValue("cv", JsonValue.Array(files.map { JsonValue.String(fileValue(it)) } + JsonValue.String("upload-1")))
        assertEquals("You sent a.pdf, b.pdf, and upload-1", form.engine.recallText("You sent {{cv}}"))
        form.close()
    }

    @Test
    fun anotherFormsUploadRecallsAsWritten() {
        val first = model(RECALLING)
        val second = model(RECALLING)
        val value = fileValue(upload(first, "cv.pdf"))
        second.setValue("cv", JsonValue.String(value))
        assertEquals(value, second.engine.recallText("{{cv}}"))
        first.close()
        second.close()
    }
}
