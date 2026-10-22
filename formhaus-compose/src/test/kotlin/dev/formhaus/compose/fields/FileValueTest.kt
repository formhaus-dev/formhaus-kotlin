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
import dev.formhaus.core.model.FormDefinition
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
import java.io.InputStream

private val references = listOf("file:///Users/x/a.pdf", "upload-123", "file:///tmp/b.png", "https://cdn.example.com/cv.pdf")
private val attachment = definition("""{"key": "doc", "type": "file", "label": "Document"}""")
private val conditional =
    definition(
        """{"key": "attach", "type": "checkbox", "label": "Attach"}""",
        """{"key": "doc", "type": "file", "label": "Document", "show": [{"field": "attach", "eq": true}]}""",
    )
private val note = definition("""{"key": "note", "type": "text", "label": "Note"}""")

@RunWith(RobolectricTestRunner::class)
class FileValueTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val cache = ApplicationProvider.getApplicationContext<Context>().cacheDir
    private val submitted = mutableListOf<JsonObject>()

    private fun form(values: String = "{}"): FormModel = model(attachment, values) { values, _ -> null.also { submitted += values } }

    private fun field(
        form: FormModel,
        key: String = "doc",
    ) = FieldContext(form.structure.visibleFields.first { it.key == key }, form)

    private fun source(): File = File.createTempFile("formhaus-cv-", ".txt").apply { writeText("cv") }

    private fun upload(
        form: FormModel,
        file: File,
    ): File = requireNotNull(runBlocking { UploadStore.copy(cache, form.uploadOwner, { file.name }) { file.inputStream() } })

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
        for (variant in listOf(
            " $value\n",
            value.replace("file:", "FILE:"),
        )) {
            assertEquals(copy, UploadStore.owned(variant, form.uploadOwner))
        }
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
    fun aPickedFileSurvivesItsFieldBeingHiddenAndShownAgain() {
        val source = source()
        val form = model(conditional, """{"attach": true}""")
        form.start()
        val copy = upload(form, source)
        field(form).replaceFile(copy)
        form.setValue("attach", JsonValue.Bool(false))
        assertNull(form.engine.values["doc"])
        form.setValue("attach", JsonValue.Bool(true))
        assertTrue(copy.exists())
        assertEquals(copy, form.pickedFile("doc"))
        form.close()
        source.delete()
    }

    @Test
    fun resetDeletesThePickedFiles() {
        val source = source()
        val form = form()
        form.start()
        val copy = upload(form, source)
        field(form).replaceFile(copy)
        form.engine.reset()
        assertFalse(copy.exists())
        assertNull(form.pickedFile("doc"))
        source.delete()
    }

    @Test
    fun aNewDefinitionKeepsTheFilesStillInTheValues() {
        val source = source()
        val form = model(conditional, """{"attach": true}""")
        form.start()
        val copy = upload(form, source)
        field(form).replaceFile(copy)
        form.engine.replaceDefinition(FormDefinition(json(attachment)))
        assertEquals(copy, form.pickedFile("doc"))
        form.engine.replaceDefinition(FormDefinition(json(note)))
        assertFalse(copy.exists())
        form.close()
        source.delete()
    }

    @Test
    fun aCopyOverTheLimitLeavesNoFileBehind() {
        val form = form()
        val huge =
            object : InputStream() {
                var left = MAX_UPLOAD_BYTES + 1

                override fun read(): Int = if (left-- > 0) 0 else -1

                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    if (left <= 0) return -1
                    val count = minOf(length.toLong(), left).toInt()
                    left -= count
                    return count
                }
            }
        val before =
            UploadStore
                .directory(cache)
                .list()
                .orEmpty()
                .toSet()
        assertNull(runBlocking { UploadStore.copy(cache, form.uploadOwner, { "big.bin" }) { huge } })
        assertEquals(
            before,
            UploadStore
                .directory(cache)
                .list()
                .orEmpty()
                .toSet(),
        )
    }

    @Test
    fun uploadNamesStaySafe() {
        assertEquals("file", uploadName(""))
        assertEquals("file", uploadName("../.."))
        assertEquals("cv.pdf", uploadName("dir/c\u0000v.pdf"))
        val long = uploadName("\uD83D\uDE00".repeat(80) + ".pdf")
        assertTrue(long.endsWith(".pdf"))
        assertTrue(long.toByteArray().size <= 200)
        assertFalse(long.dropLast(4).last().isHighSurrogate())
    }

    @Test
    fun acceptListsMapToMimeTypes() {
        assertEquals(listOf(ANY_FILE), acceptedTypes(null))
        assertEquals(listOf("image/png", "application/pdf"), acceptedTypes("image/png, .PDF"))
    }
}
