package dev.formhaus.compose.form

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.formhaus.compose.model.focus
import dev.formhaus.compose.model.model
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private fun choices(type: String) =
    """
    {"id": "c", "title": "C", "submit": {"label": "Send"}, "fields": [
      {"key": "name", "type": "text", "label": "Name", "validation": {"minLength": 2, "maxLength": 40}},
      {"key": "age", "type": "number", "label": "Age", "validation": {"minLength": 1, "maxLength": 3}},
      {"key": "plan", "type": "$type", "label": "Plan", "options": [
        {"value": "a", "label": "Alpha"}, {"value": "b", "label": "Beta"}, {"value": "c", "label": "Gamma"}]}]}
    """.trimIndent()

@RunWith(RobolectricTestRunner::class)
class ChoiceFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private fun focusPlan(
        type: String,
        values: String,
        components: FormComponents,
    ) {
        val form = model(choices(type), values)
        compose.setContent { Provided(components) { FormhausFormContent(form) } }
        compose.waitForIdle()
        compose.runOnIdle { form.focus("plan") }
        compose.waitForIdle()
    }

    private fun option(label: String): SemanticsMatcher = hasText(label) and (isSelectable() or isToggleable())

    @Test
    fun aRadioGroupFocusesItsCheckedOption() {
        focusPlan("radio", """{"plan": "b"}""", FormComponents.standard)
        compose.onNode(option("Beta")).assertIsFocused()
    }

    @Test
    fun aSystemRadioListFocusesItsCheckedOption() {
        focusPlan("radio", """{"plan": "c"}""", FormComponents.system)
        compose.onNode(option("Gamma")).assertIsFocused()
    }

    @Test
    fun aRadioGroupWithoutAnAnswerFocusesItsFirstOption() {
        focusPlan("radio", "{}", FormComponents.standard)
        compose.onNode(option("Alpha")).assertIsFocused()
    }

    @Test
    fun aMultiselectFocusesItsFirstOption() {
        focusPlan("multiselect", """{"plan": ["c"]}""", FormComponents.standard)
        compose.onNode(option("Alpha")).assertIsFocused()
    }

    @Test
    fun textFieldsExposeTheirLengthLimits() {
        val form = model(choices("radio"))
        val fields = form.structure.visibleFields.associateBy { it.key }
        val name = FieldContext(fields.getValue("name"), form)
        val age = FieldContext(fields.getValue("age"), form)
        assertEquals(2.0, name.minLength)
        assertEquals(40.0, name.maxLength)
        assertNull(age.minLength)
        assertNull(age.maxLength)
    }
}
