package dev.formhaus.compose.fields

import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.formhaus.compose.model.json
import dev.formhaus.compose.model.model
import dev.formhaus.compose.model.setValue
import dev.formhaus.core.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
class HostileNumberTest(
    private val number: String,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun hugeNegativeAndFractionalNumbersNeverTrap() {
        val options = """[{"value": "1", "label": "One", "image": "data:image/png;base64,AA=="}, {"value": "2", "label": "Two"}]"""
        val fields =
            arrayOf(
                """{"key": "bio", "type": "textarea", "label": "Bio", "rows": $number}""",
                """{"key": "pick", "type": "radio", "label": "Pick", "columns": $number, "options": $options}""",
                """{"key": "many", "type": "multiselect", "label": "Many", "columns": $number, "options": $options}""",
                """{"key": "score", "type": "scale", "label": "Score", "validation": {"min": $number, "max": $number}}""",
                """{"key": "low", "type": "scale", "label": "Low", "validation": {"min": $number}}""",
                """{"key": "stars", "type": "rating", "label": "Stars", "validation": {"max": $number}}""",
                """{"key": "text", "type": "text", "label": "Text", "validation": {"minLength": $number, "maxLength": $number}}""",
                """{"key": "age", "type": "number", "label": "Age"}""",
            )
        val checked = runCatching { model(definition(*fields)) }.isSuccess
        val safe = fields.map { it.replace("\"min\": $number, \"max\": $number", "\"min\": 1, \"max\": 5").replace(number, "3") }
        val shown = if (checked) fields else safe.toTypedArray()
        val values = """{"score": $number, "stars": $number, "pick": $number, "text": $number, "age": $number, "bio": $number}"""
        val form = model(definition(*shown), values)
        compose.setContent { Themed { FieldList(form) } }
        compose.waitForIdle()
        compose.runOnIdle {
            form.engine.validate()
            form.setValue("low", json(number))
            form.setValue("age", JsonValue.Number(-1e300))
        }
        compose.waitForIdle()
        assertEquals(fields.size, form.structure.nodes.size)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun numbers(): List<Array<Any>> = listOf("1e300", "-1e300", "-3", "2.5", "0", "1e15").map { arrayOf(it) }
    }
}
