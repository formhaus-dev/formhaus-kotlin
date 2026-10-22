package dev.formhaus.compose.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.addItem
import dev.formhaus.compose.model.back
import dev.formhaus.compose.model.focus
import dev.formhaus.compose.model.model
import dev.formhaus.compose.model.next
import dev.formhaus.compose.model.requestFocus
import dev.formhaus.compose.model.setErrors
import dev.formhaus.compose.model.setValue
import dev.formhaus.compose.model.submit
import dev.formhaus.core.engine.SubmitResult
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val contact =
    """
    {"id": "c", "title": "C", "submit": {"label": "Send"}, "fields": [
      {"key": "name", "type": "text", "label": "Name"}, {"key": "email", "type": "email", "label": "Email"}]}
    """.trimIndent()

@RunWith(RobolectricTestRunner::class)
class FocusTest {
    @get:Rule
    val compose = createComposeRule()

    private fun probeForm(
        definition: String,
        values: String = "{}",
    ): Pair<FormModel, CallLog> {
        val form = model(definition, values)
        val log = CallLog()
        compose.setContent { Provided(probes(log)) { FormhausFormContent(form) } }
        compose.waitForIdle()
        return form to log
    }

    private fun CallLog.focused(): List<String> = calls.filter { it.startsWith("focus:") }

    @Test
    fun anErrorSummaryLinkToAnotherStepFocusesTheFieldOnceItsStepRenders() {
        val (form, log) = probeForm(twoSteps(required = false))
        compose.act(form) { next() }
        compose.runOnIdle {
            form.setErrors(stringMapOf("a" to "bad"))
            form.focus("a")
        }
        compose.waitForIdle()
        assertEquals("one", form.structure.currentStep?.id)
        assertEquals(listOf("focus:a"), log.focused())
        compose.runOnIdle { form.focus("a") }
        compose.waitForIdle()
        assertEquals(listOf("focus:a", "focus:a"), log.focused())
    }

    @Test
    fun aSubmitThatReturnsToAnInvalidStepFocusesItsField() {
        val (form, log) = probeForm(twoSteps(required = true), """{"a": "x"}""")
        compose.act(form) { next() }
        compose.runOnIdle { form.setValue("a", JsonValue.String("")) }
        compose.act(form) { submit() }
        assertEquals("one", form.structure.currentStep?.id)
        assertEquals("focus:a", log.focused().last())
    }

    @Test
    fun addingAGroupItemFocusesItsFirstField() {
        val definition =
            """
            {"id": "g", "title": "G", "submit": {"label": "Send"},
             "fields": [{"key": "team", "type": "group", "label": "Team", "fields": [{"key": "name", "type": "probe", "label": "Name"}]}]}
            """.trimIndent()
        val (form, log) = probeForm(definition)
        assertTrue(form.group("team").items.isEmpty())
        compose.runOnIdle { form.addItem("team") }
        compose.waitForIdle()
        assertEquals(1, form.group("team").items.size)
        assertEquals(listOf("focus:team.0.name"), log.focused())
    }

    @Test
    fun aStaleRequestDoesNotFireWhenAnotherFieldAskedLater() {
        val (form, log) = probeForm(twoSteps(required = false))
        compose.act(form) { next() }
        compose.runOnIdle {
            form.requestFocus("a")
            form.requestFocus("b")
        }
        compose.act(form) { back() }
        assertFalse("focus:a" in log.focused())
    }

    @Test
    fun aFocusRequestForAHiddenFieldDoesNotFireAfterTheUserMovesOn() {
        val definition =
            """
            {"id": "p", "title": "P", "submit": {"label": "Send"}, "fields": [
              {"key": "a", "type": "probe", "label": "A"},
              {"key": "b", "type": "probe", "label": "B", "show": [{"field": "a", "eq": "x"}]}]}
            """.trimIndent()
        val (form, log) = probeForm(definition)
        compose.runOnIdle { form.focus("b") }
        compose.waitForIdle()
        compose.runOnIdle { form.setValue("a", JsonValue.String("x")) }
        compose.waitForIdle()
        assertTrue(log.focused().isEmpty())
    }

    private fun gated(gate: CompletableDeferred<Unit>): FormModel =
        model(contact, """{"email": "ada@x.dev"}""") { _, _ ->
            gate.await()
            SubmitResult.Rejected(stringMapOf("email" to "Taken"))
        }

    @Test
    fun theFocusedFieldKeepsFocusWhileASubmitRunsAndAfterAServerError() {
        val gate = CompletableDeferred<Unit>()
        val form = gated(gate)
        compose.setContent { FormhausForm(form) }
        compose.runOnIdle { form.focus("email") }
        compose.waitForIdle()
        val email = compose.onNode(hasContentDescription("Email") and hasSetTextAction())
        email.assertIsFocused()
        compose.act(form) { submit() }
        assertTrue(form.disabled)
        email.assertIsFocused()
        val fields = form.structure.visibleFields
        assertFalse(FieldContext(fields.last(), form).isLocked)
        assertTrue(FieldContext(fields.first(), form).isLocked)
        compose.runOnIdle { gate.complete(Unit) }
        compose.waitForIdle()
        assertEquals("Taken", form.field("email").error)
        email.assertIsFocused()
    }

    @Test
    fun textTypedIntoTheFocusedFieldDuringASubmitAppliesOnceTheFormUnlocks() {
        val gate = CompletableDeferred<Unit>()
        val form = gated(gate)
        compose.setContent { FormhausForm(form) }
        compose.runOnIdle { form.focus("email") }
        compose.act(form) { submit() }
        val email = compose.onNode(hasContentDescription("Email") and hasSetTextAction())
        email.performTextInputSelection(TextRange(9))
        email.performTextInput("m")
        assertEquals(JsonValue.String("ada@x.dev"), form.field("email").value)
        compose.runOnIdle { gate.complete(Unit) }
        compose.waitForIdle()
        assertEquals(JsonValue.String("ada@x.devm"), form.field("email").value)
    }

    @Test
    fun aFieldThatLeavesTheScreenStopsCountingAsFocused() {
        val form = model(contact)
        var shown by mutableStateOf(true)
        compose.setContent { if (shown) FormhausFormContent(form) }
        compose.runOnIdle { form.focus("name") }
        compose.waitForIdle()
        assertTrue(form.field("name").hasFocus)
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        assertFalse(form.field("name").hasFocus)
    }
}
