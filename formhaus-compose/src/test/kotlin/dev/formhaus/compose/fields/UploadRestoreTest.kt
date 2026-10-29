package dev.formhaus.compose.fields

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.FormRendererOptions
import dev.formhaus.compose.model.MainDispatcherRule
import dev.formhaus.compose.model.json
import dev.formhaus.compose.model.ownedFormModel
import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.engine.FormSnapshot
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.model.FormDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import java.util.UUID

private val papers =
    FormDefinition(
        json(
            """{"id": "papers", "title": "Papers", "submit": {"label": "Send"}, "fields": [
              {"key": "doc", "type": "file", "label": "Document"},
              {"key": "extra", "type": "file", "label": "Extra"}]}""",
        ),
    )

@RunWith(RobolectricTestRunner::class)
class UploadRestoreTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val cache = context.cacheDir
    private val elsewhere = Files.createTempDirectory("formhaus-other-cache").toFile()

    private fun stored(name: String): File = File(File(UploadStore.directory(cache).canonicalFile, UUID.randomUUID().toString()), name)

    private fun snapshot(
        dead: File,
        live: File,
    ): FormSnapshot {
        val values = """{"doc": "${fileValue(dead)}", "extra": "${fileValue(live)}"}"""
        return requireNotNull(
            FormSnapshot.fromJson("""{"version":1,"definitionId":"papers","values":$values,"step":null,"skipped":[],"seed":""}"""),
        )
    }

    private fun assertRestored(
        build: (FormSnapshot) -> FormModel,
        attachLate: Boolean,
    ) {
        UploadStore.attach(elsewhere)
        val dead = stored("gone.pdf")
        val live = stored("kept.pdf").apply { parentFile?.mkdirs() }.apply { writeText("cv") }
        val form = build(snapshot(dead, live))
        if (attachLate) form.attachUploads(cache)
        val values = form.engine.values
        assertEquals(JsonValue.Null, values["doc"])
        assertEquals(fileValue(live), values["extra"]?.stringValue)
        assertEquals(live, form.pickedFile("extra"))
        form.close()
        assertFalse(live.exists())
    }

    @Test
    fun everyConstructionPathDropsDeadUploadsAndAdoptsLiveOnes() {
        assertRestored({ snapshot ->
            ownedFormModel(
                context,
                papers,
                MutableJsonObject(),
                FormEngineOptions(),
                FormRendererOptions(),
                snapshot,
                null,
            ) { _, _ -> null }
        }, attachLate = false)
        assertRestored({ snapshot -> FormModel(papers, restoring = snapshot) { _, _ -> null } }, attachLate = true)
        assertRestored({ snapshot -> FormModel(FormEngine(papers, restoring = snapshot)) { _, _ -> null } }, attachLate = true)
    }

    @Test
    fun aFileListDropsItsDeadUploadsAndKeepsTheRestInOrder() {
        val album =
            FormDefinition(
                json(
                    """{"id": "papers", "title": "Papers", "formatVersion": "1.7", "submit": {"label": "Send"}, "fields": [
                      {"key": "docs", "type": "file", "label": "Documents", "multiple": true}]}""",
                ),
            )
        UploadStore.attach(elsewhere)
        val dead = stored("gone.pdf")
        val live = stored("kept.pdf").apply { parentFile?.mkdirs() }.apply { writeText("cv") }
        val values = """{"docs": ["upload-1", "${fileValue(dead)}", "${fileValue(live)}"]}"""
        val snapshot =
            requireNotNull(
                FormSnapshot.fromJson("""{"version":1,"definitionId":"papers","values":$values,"step":null,"skipped":[],"seed":""}"""),
            )
        val form = FormModel(album, restoring = snapshot) { _, _ -> null }
        form.attachUploads(cache)
        assertEquals(
            JsonValue.Array(listOf(JsonValue.String("upload-1"), JsonValue.String(fileValue(live)))),
            form.engine.values["docs"],
        )
        assertEquals(listOf(live), form.pickedFiles("docs"))
        form.close()
        assertFalse(live.exists())
    }
}
