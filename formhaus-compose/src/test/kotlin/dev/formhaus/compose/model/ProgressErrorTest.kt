package dev.formhaus.compose.model

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private val definition =
    """
    {"id": "p", "title": "P", "submit": {"label": "Send"},
     "steps": [
       {"id": "one", "title": "One", "fields": [
         {"key": "a", "type": "text", "label": "A"}, {"key": "b", "type": "text", "label": "B"}]},
       {"id": "two", "title": "Two", "fields": [{"key": "c", "type": "text", "label": "C"}]}
     ]}
    """.trimIndent()

private fun FormModel.currentStepErrors(): Int = structure.currentStep?.let { stepErrorCounts[it.id] } ?: 0

class ProgressErrorTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun theCurrentStepCarriesItsErrorCountAndLabel() {
        val form = model(definition)
        assertEquals(0, form.currentStepErrors())
        form.setErrors(stringMapOf("a" to "Bad", "b" to "Bad"))
        assertEquals(2, form.currentStepErrors())
        assertEquals("Errors: 2", form.label("stepErrors", stringMapOf("count" to JsonValue.Number(2))))
    }

    @Test
    fun theCountReturnsToZeroOnceTheErrorsAreFixed() {
        val form = model(definition)
        form.setErrors(stringMapOf("a" to "Bad"))
        assertEquals(1, form.currentStepErrors())
        form.setErrors(stringMapOf())
        assertEquals(0, form.currentStepErrors())
    }
}
