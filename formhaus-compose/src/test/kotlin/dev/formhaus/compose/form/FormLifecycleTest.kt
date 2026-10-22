package dev.formhaus.compose.form

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.FormRendererOptions
import dev.formhaus.compose.model.json
import dev.formhaus.compose.model.model
import dev.formhaus.compose.model.submit
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.engine.SubmitHandler
import dev.formhaus.core.engine.SubmitResult
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormDefinition
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private fun probeDefinition(
    id: String,
    key: String,
): String = """{"id": "$id", "title": "T", "submit": {"label": "Send"}, "fields": [{"key": "$key", "type": "probe", "label": "X"}]}"""

private fun parsed(
    id: String,
    key: String,
): FormDefinition = FormDefinition(json(probeDefinition(id, key)))

@RunWith(RobolectricTestRunner::class)
class FormLifecycleTest {
    @get:Rule
    val compose = createComposeRule()

    private val seen = mutableListOf<FormModel>()

    private val capturing: FormComponents =
        FormComponents.standard.field("probe") { context ->
            if (seen.lastOrNull() !== context.model) seen += context.model
            Text(context.key)
        }

    @Test
    fun aNewModelPassedToFormhausFormReplacesTheOldOne() {
        val log = CallLog()
        var current by mutableStateOf(model(probeDefinition("a", "x")))
        compose.setContent { Provided(probes(log)) { FormhausForm(current) } }
        compose.waitForIdle()
        assertTrue("body:x" in log.calls)
        compose.runOnIdle { current = model(probeDefinition("b", "y")) }
        compose.waitForIdle()
        assertTrue("body:y" in log.calls)
    }

    @Test
    fun theSameDefinitionKeepsTheModelAndCallsTheLatestHandlers() {
        val calls = mutableListOf<String>()
        val first: SubmitHandler = { _, _ ->
            calls += "first"
            null
        }
        val second: SubmitHandler = { _, _ ->
            calls += "second"
            null
        }
        var handler by mutableStateOf(first)
        var options by mutableStateOf(FormEngineOptions())
        compose.setContent {
            Provided(capturing) { FormhausForm(parsed("a", "x"), engineOptions = options, onSubmit = handler) }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            handler = second
            options =
                FormEngineOptions(onBeforeSubmit = {
                    calls += "before"
                    null
                })
        }
        compose.waitForIdle()
        compose.act(seen.single()) { submit() }
        assertEquals(1, seen.size)
        assertEquals(listOf("before", "second"), calls)
    }

    @Test
    fun aNewDefinitionBuildsANewModelAndLetsASendingSubmitFinish() {
        val gate = CompletableDeferred<SubmitResult?>()
        val after = mutableListOf<String>()
        val options = FormEngineOptions(onAfterSubmit = { _, _ -> after += "after" })
        var definition by mutableStateOf(parsed("a", "x"))
        compose.setContent { Provided(capturing) { FormhausForm(definition, engineOptions = options) { _, _ -> gate.await() } } }
        compose.waitForIdle()
        val first = seen.single()
        compose.act(first) { submit() }
        assertTrue(first.submitting)
        compose.runOnIdle { definition = parsed("b", "y") }
        compose.waitForIdle()
        assertEquals(2, seen.size)
        assertNotSame(first, seen.last())
        assertTrue(first.submitting)
        assertFalse(seen.last().submitting)
        compose.runOnIdle { gate.complete(SubmitResult.Ok) }
        compose.waitForIdle()
        assertFalse(first.engine.submitting)
        assertEquals(listOf("after"), after)
    }

    @Test
    fun aNewDefinitionCancelsASubmitThatHasNotStartedSending() {
        val gate = CompletableDeferred<Boolean?>()
        val sent = mutableListOf<String>()
        val options = FormEngineOptions(onBeforeSubmit = { gate.await() })
        var definition by mutableStateOf(parsed("a", "x"))
        compose.setContent {
            Provided(capturing) { FormhausForm(definition, engineOptions = options) { _, _ -> null.also { sent += "sent" } } }
        }
        compose.waitForIdle()
        val first = seen.single()
        compose.act(first) { submit() }
        assertTrue(first.submitting)
        compose.runOnIdle { definition = parsed("b", "y") }
        compose.waitForIdle()
        assertFalse(first.engine.submitting)
        compose.runOnIdle { gate.complete(true) }
        compose.waitForIdle()
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun anOwnedModelKeepsRunningWhenTheFormLeavesCompositionWithinItsOwner() {
        var shown by mutableStateOf(true)
        val values = mutableListOf<String>()
        val renderer =
            FormRendererOptions(onValuesChange = { values += it.toString() })
        compose.setContent {
            Provided(capturing) { if (shown) FormhausForm(parsed("a", "x"), renderer = renderer) { _, _ -> null } }
        }
        compose.waitForIdle()
        val owned = seen.single()
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        val before = values.size
        compose.runOnIdle { owned.engine.setValue("x", JsonValue.String("late")) }
        assertEquals(before + 1, values.size)
        compose.runOnIdle { shown = true }
        compose.waitForIdle()
        assertSame(owned, seen.single())
        assertEquals("late", owned.engine.values["x"]?.stringValue)
    }
}
