package dev.formhaus.compose.model

import android.os.Looper
import dev.formhaus.core.engine.FormSnapshot
import dev.formhaus.core.engine.SubmitResult
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormDefinition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

private const val STEPS =
    """{"id": "trip", "title": "Trip", "submit": {"label": "Book"}, "steps": [
      {"id": "who", "title": "Who", "fields": [{"key": "name", "type": "text", "label": "Name"}]},
      {"id": "seat", "title": "Seat", "fields": [{"key": "seat", "type": "text", "label": "Seat"}]}]}"""

@RunWith(RobolectricTestRunner::class)
class SnapshotEmitTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val emitted = mutableListOf<FormSnapshot?>()

    private fun form(restoring: FormSnapshot? = null): FormModel =
        FormModel(FormDefinition(json(STEPS)), restoring = restoring, onSnapshotChange = { emitted += it }) { _, _ -> SubmitResult.Ok }

    private fun frame() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
    }

    @Test
    fun changesInOneFrameEmitOneSnapshot() {
        val form = form()
        form.start()
        form.setValue("name", JsonValue.String("A"))
        form.setValue("name", JsonValue.String("Ada"))
        frame()
        assertEquals(1, emitted.size)
        assertEquals(
            "Ada",
            emitted
                .single()
                ?.values
                ?.get("name")
                ?.stringValue,
        )
        runBlocking { form.next() }
        frame()
        assertEquals("seat", emitted.last()?.step)
    }

    @Test
    fun restoringEmitsNothingAndASuccessfulSubmitEmitsNullOnce() {
        val snapshot =
            FormSnapshot.fromJson(
                """{"version":1,"definitionId":"trip","values":{"name":"Ada"},"step":"seat","skipped":[],"seed":""}""",
            )
        val form = form(snapshot)
        form.start()
        frame()
        assertEquals(emptyList<FormSnapshot?>(), emitted)
        form.setValue("seat", JsonValue.String("12A"))
        runBlocking { form.submit() }
        frame()
        assertEquals(1, emitted.size)
        assertNull(emitted.single())
    }
}
