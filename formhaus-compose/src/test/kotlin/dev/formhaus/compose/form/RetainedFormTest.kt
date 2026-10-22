package dev.formhaus.compose.form

import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import dev.formhaus.compose.fields.TextInputField
import dev.formhaus.compose.fields.UploadStore
import dev.formhaus.compose.fields.fileValue
import dev.formhaus.compose.fields.pickedFile
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.json
import dev.formhaus.compose.model.next
import dev.formhaus.compose.model.setValue
import dev.formhaus.compose.model.skip
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.engine.snapshot
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.research.Research
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.io.File
import java.time.Duration
import java.util.UUID

class HostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { content() }
    }

    companion object {
        var content: @Composable () -> Unit = {}
    }
}

internal val tripJson =
    """
    {"id": "trip", "title": "Trip", "submit": {"label": "Book"}, "steps": [
      {"id": "who", "title": "Who", "fields": [{"key": "name", "type": "text", "label": "Name", "validation": {"required": true}}]},
      {"id": "extras", "title": "Extras", "skip": {"label": "Skip"}, "fields": [{"key": "meal", "type": "text", "label": "Meal"}]},
      {"id": "seat", "title": "Seat", "fields": [
        {"key": "note", "type": "text", "label": "Note"},
        {"key": "doc", "type": "file", "label": "Ticket"},
        {"key": "photo", "type": "file", "label": "Photo"},
        {"key": "pick", "type": "radio", "label": "Pick", "randomize": true,
         "options": [{"value": "a", "label": "A"}, {"value": "b", "label": "B"}, {"value": "c", "label": "C"}]}]}
    ]}
    """.trimIndent()

internal val trip = FormDefinition(json(tripJson))

internal fun upload(name: String): File {
    val cache = ApplicationProvider.getApplicationContext<Context>().cacheDir
    val folder = File(UploadStore.directory(cache).canonicalFile, UUID.randomUUID().toString()).apply { mkdirs() }
    return File(folder, name).apply { writeText("ticket") }
}

internal fun settle() {
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
}

@RunWith(RobolectricTestRunner::class)
class RetainedFormTest {
    private val seen = mutableListOf<FormModel>()
    private val options = FormEngineOptions(research = Research())

    private val capturing: FormComponents =
        FormComponents.standard.field("text") { context ->
            if (seen.lastOrNull() !== context.model) seen += context.model
            TextInputField(context)
        }

    @After
    fun clear() {
        HostActivity.content = {}
    }

    private fun launch(saved: Bundle? = null): ActivityController<HostActivity> {
        HostActivity.content = { Provided(capturing) { FormhausForm(trip, engineOptions = options) { _, _ -> null } } }
        val controller = Robolectric.buildActivity(HostActivity::class.java)
        if (saved == null) controller.setup() else controller.setup(saved)
        settle()
        return controller
    }

    private fun FormModel.reachSeat() {
        setValue("name", JsonValue.String("Ada"))
        scope.launch {
            next()
            skip()
        }
        settle()
    }

    @Test
    fun aConfigurationChangeKeepsTheModelValuesStepAndUploads() {
        val controller = launch()
        val model = seen.last()
        model.reachSeat()
        model.setValue("note", JsonValue.String("Window please"))
        val ticket = upload("ticket.pdf")
        model.setValue("doc", JsonValue.String(fileValue(ticket)))
        controller.recreate()
        settle()
        val after = seen.last()
        assertSame(model, after)
        assertEquals("seat", after.engine.currentStep?.id)
        assertEquals("Window please", after.engine.values["note"]?.stringValue)
        assertTrue(ticket.exists())
        controller.pause().stop().destroy()
    }

    @Test
    fun processDeathRestoresValuesStepSkippedStepsSeedAndLiveUploads() {
        val first = launch()
        val model = seen.last()
        model.reachSeat()
        val ticket = upload("ticket.pdf")
        val photo = upload("photo.png")
        model.setValue("doc", JsonValue.String(fileValue(ticket)))
        model.setValue("photo", JsonValue.String(fileValue(photo)))
        settle()
        val before = model.engine.snapshot()
        val saved = Bundle()
        first.saveInstanceState(saved)
        photo.parentFile?.deleteRecursively()
        val restored = launch(saved).let { seen.last() }
        assertNotSame(model, restored)
        val snapshot = restored.engine.snapshot()
        assertEquals("seat", snapshot.step)
        assertEquals(listOf("extras"), snapshot.skipped)
        assertEquals(before.seed, snapshot.seed)
        assertEquals("Ada", snapshot.values["name"]?.stringValue)
        assertEquals(ticket, restored.pickedFile("doc"))
        assertNull(restored.engine.values["photo"]?.stringValue)
        restored.close()
        assertTrue(!ticket.exists())
    }
}
