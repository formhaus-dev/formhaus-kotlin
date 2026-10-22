package dev.formhaus.compose.fields

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.MainDispatcherRule
import dev.formhaus.compose.model.json
import dev.formhaus.compose.model.model
import dev.formhaus.compose.model.setValue
import dev.formhaus.compose.model.submit
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

private val references = listOf("file:///Users/x/a.pdf", "upload-123", "file:///tmp/b.png", "https://cdn.example.com/cv.pdf")
private val attachment = definition("""{"key": "doc", "type": "file", "label": "Document"}""")

@RunWith(RobolectricTestRunner::class)
class FileValueTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val cache = ApplicationProvider.getApplicationContext<Context>().cacheDir
    private val submitted = mutableListOf<JsonObject>()

    private fun form(values: String = "{}"): FormModel = model(attachment, values) { values, _ -> null.also { submitted += values } }

    private fun field(form: FormModel) = FieldContext(form.structure.visibleFields.first(), form)

    private fun source(): File = File.createTempFile("formhaus-cv-", ".txt").apply { writeText("cv") }

    private fun upload(
        form: FormModel,
        file: File,
    ): File = requireNotNull(runBlocking { UploadStore.copy(cache, file.name, form) { file.inputStream() } })

    @Test
    fun anAppReferenceInTheInitialValuesIsSubmittedAndNotShown() {
        for (reference in references) {
            submitted.clear()
            val form = form("""{"doc": "$reference"}""")
            form.start()
            assertNull(field(form).fileName)
            runBlocking { form.submit() }
            assertEquals(listOf(json("""{"doc": "$reference"}""").objectValue), submitted)
        }
    }

    @Test
    fun setValueDoesNotShowAnAppReferenceAsAPickedFile() {
        for (reference in references) {
            val form = form()
            form.start()
            form.setValue("doc", JsonValue.String(reference))
            assertNull(field(form).fileName)
            assertNull(form.pickedFile("doc"))
        }
    }

    @Test
    fun aReferenceShowsAsPlainTextAndCanBeCleared() {
        for (reference in references) {
            val form = form("""{"doc": "$reference"}""")
            form.start()
            val context = field(form)
            assertEquals(reference, context.reference)
            assertEquals(reference, context.fileValueText)
            context.setValue(JsonValue.Null)
            assertNull(field(form).reference)
            assertEquals(JsonValue.Null, form.engine.getSubmitValues()["doc"] ?: JsonValue.Null)
        }
    }

    @Test
    fun aPickedFileShowsItsNameAndIsSubmitted() {
        val source = source()
        val form = form()
        form.start()
        val copy = upload(form, source)
        field(form).replaceFile(copy)
        assertEquals(source.name, field(form).fileName)
        assertNull(field(form).reference)
        assertEquals(copy, form.pickedFile("doc"))
        val value = fileValue(copy)
        assertTrue(value.startsWith("file:///"))
        for (variant in listOf(" $value\n", value.replace("file:", "FILE:"))) assertEquals(copy, UploadStore.owned(variant, form))
        runBlocking { form.submit() }
        assertEquals(listOf(json("""{"doc": "$value"}""").objectValue), submitted)
        form.close()
        assertNull(field(form).fileName)
        assertFalse(copy.exists())
        source.delete()
    }

    @Test
    fun aFilePickedOnOneFormIsNotAnUploadOfAnotherForm() {
        val source = source()
        val first = form()
        val second = form()
        val copy = upload(first, source)
        first.setValue("doc", JsonValue.String(fileValue(copy)))
        second.setValue("doc", JsonValue.String(fileValue(copy)))
        assertEquals(copy, first.pickedFile("doc"))
        assertNull(second.pickedFile("doc"))
        assertNull(field(second).fileName)
        assertEquals(fileValue(copy), field(second).reference)
        first.close()
        source.delete()
    }

    @Test
    fun replacedUploadsArePrunedFromDisk() {
        val source = source()
        val form = form()
        form.start()
        val old = upload(form, source)
        field(form).replaceFile(old)
        val new = upload(form, source)
        field(form).replaceFile(new)
        assertFalse(old.exists())
        assertTrue(new.exists())
        field(form).replaceFile(null)
        assertFalse(new.exists())
        source.delete()
    }

    @Test
    fun acceptListsMapToMimeTypes() {
        assertEquals(listOf(ANY_FILE), acceptedTypes(null))
        assertEquals(listOf("image/png", "application/pdf"), acceptedTypes("image/png, .PDF"))
    }
}
