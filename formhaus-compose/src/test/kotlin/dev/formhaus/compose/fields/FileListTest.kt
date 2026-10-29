package dev.formhaus.compose.fields

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.json
import dev.formhaus.compose.model.model
import dev.formhaus.core.json.JsonValue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

private fun papers(
    version: String,
    extra: String = "",
) = """{"id": "f", "title": "F", "formatVersion": "$version", "submit": {"label": "Send"}, "fields": [
       {"key": "docs", "type": "file", "label": "Documents", "multiple": true$extra}]}"""

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

    private fun render(
        version: String = "1.7",
        extra: String = "",
        values: String = "{}",
        picks: Picks = Picks(),
    ): FormModel {
        val form = model(papers(version, extra), values)
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picks) { Themed { FieldList(form) } }
        }
        compose.waitForIdle()
        return form
    }

    private fun settle(done: () -> Boolean) {
        val end = System.currentTimeMillis() + TIMEOUT
        while (!done()) {
            check(System.currentTimeMillis() < end) { "the pick did not finish" }
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
        }
    }

    private fun context(form: FormModel) = FieldContext(form.structure.visibleFields.first(), form)

    private fun announced(form: FormModel): String? = form.alerts.announcement?.text

    @Test
    fun pickedFilesAddToTheListAndEachHasANamedRemoveButton() {
        val form = render()
        val files = listOf(upload(form, "a.pdf"), upload(form, "b.pdf"), upload(form, "c.pdf"))
        compose.runOnIdle { context(form).addFiles(files.take(2), 0) }
        compose.runOnIdle { context(form).addFiles(files.drop(2), 0) }
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
    fun everyListItemWithANameGetsARowAndReferencesShowTheirLastSegment() {
        val form =
            render(
                values = """{"docs": ["https://cdn.example.com/files/w9.pdf", "upload-1", {"name": "scan.pdf"}, 7, {"size": 1}]}""",
            )
        val file = upload(form, "a.pdf")
        compose.runOnIdle { context(form).addFiles(listOf(file), 0) }
        for (name in listOf("w9.pdf", "upload-1", "scan.pdf", "a.pdf")) compose.onNodeWithContentDescription("Remove $name").assertExists()
        compose.onNodeWithText("https://cdn.example.com/files/w9.pdf").assertDoesNotExist()
        compose.onAllNodesWithText("Remove").assertCountEquals(4)
        compose.onNodeWithContentDescription("Remove w9.pdf").performClick()
        compose.waitForIdle()
        assertEquals("w9.pdf removed", announced(form))
        assertEquals(
            json("""["upload-1", {"name": "scan.pdf"}, 7, {"size": 1}, "${fileValue(file)}"]"""),
            form.engine.values["docs"],
        )
        form.close()
    }

    @Test
    fun pickingStopsAtTheLimitAndAnnouncesTheDroppedFiles() {
        val picks = Picks(sources("a.pdf", "b.pdf", "c.pdf", "d.pdf"))
        val form = render(extra = """, "validation": {"maxLength": 3}""", values = """{"docs": ["upload-1"]}""", picks = picks)
        compose.onNode(picker).performClick()
        settle { form.pickedFiles("docs").size == 2 }
        assertTrue(picks.launched.single() is ActivityResultContracts.OpenMultipleDocuments)
        assertEquals(listOf("a.pdf", "b.pdf"), form.pickedFiles("docs").map { it.name })
        assertEquals("a.pdf, b.pdf added At most 3 allowed", announced(form))
        compose.onNodeWithText("At most 3 allowed").assertExists()
        compose.onNodeWithContentDescription("Remove b.pdf").assertIsFocused()
        compose.onNode(picker).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Remove b.pdf").performClick()
        compose.onNodeWithText("At most 3 allowed").assertDoesNotExist()
        compose.onNode(picker).assertIsEnabled()
        compose.onNode(picker).performClick()
        settle { form.pickedFiles("docs").size == 2 && announced(form) != "b.pdf removed" }
        assertTrue(picks.launched.last() is ActivityResultContracts.OpenDocument)
        assertEquals("a.pdf added", announced(form))
        form.close()
    }

    @Test
    fun imageListsUseThePhotoPickersAndTheCustomLimitMessage() {
        val picks = Picks(sources("a.png", "b.png", "c.png"))
        val form =
            render(
                extra = """, "accept": "image/png", "validation": {"maxLength": 2, "maxLengthMessage": "Two photos at most"}""",
                picks = picks,
            )
        compose.onNode(picker).performClick()
        settle { form.pickedFiles("docs").size == 2 }
        assertTrue(picks.launched.single() is ActivityResultContracts.PickMultipleVisualMedia)
        assertEquals("a.png, b.png added Two photos at most", announced(form))
        compose.onNodeWithContentDescription("Remove b.png").performClick()
        compose.onNode(picker).performClick()
        settle { announced(form) == "a.png added" }
        assertTrue(picks.launched.last() is ActivityResultContracts.PickVisualMedia)
        form.close()
    }

    @Test
    fun aNegativeLimitDisablesThePicker() {
        val negative = render(extra = """, "validation": {"maxLength": -3, "minLength": -5}""", picks = Picks(sources("a.pdf")))
        compose.onNode(picker).assertIsNotEnabled()
        negative.close()
    }

    @Test
    fun aHugeLimitIsCappedAndPicksFiles() {
        val picks = Picks(sources("a.pdf", "b.pdf"))
        val huge = render(extra = """, "validation": {"maxLength": 1e300}""", picks = picks)
        compose.onNode(picker).performClick()
        settle { huge.pickedFiles("docs").size == 2 }
        assertTrue(picks.launched.single() is ActivityResultContracts.OpenMultipleDocuments)
        huge.close()
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

private val picker = hasContentDescription("Documents").and(hasClickAction())

private const val TIMEOUT = 5_000L

private fun sources(vararg names: String): List<Uri> =
    names.map { name ->
        val folder = Files.createTempDirectory("formhaus-pick").toFile()
        Uri.fromFile(File(folder, name).apply { writeText(name) })
    }

class Picks(
    private val uris: List<Uri> = emptyList(),
) : ActivityResultRegistry(),
    ActivityResultRegistryOwner {
    val launched = mutableListOf<ActivityResultContract<*, *>>()

    override val activityResultRegistry: ActivityResultRegistry get() = this

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launched += contract
        val single = contract is ActivityResultContracts.OpenDocument || contract is ActivityResultContracts.PickVisualMedia
        dispatchResult(requestCode, if (single) uris.firstOrNull() else uris)
    }
}
