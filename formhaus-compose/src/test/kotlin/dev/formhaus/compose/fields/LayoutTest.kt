package dev.formhaus.compose.fields

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.LocalMeasuredFrames
import dev.formhaus.compose.components.MeasuredFrames
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.model
import dev.formhaus.compose.theme.FormhausTheme
import dev.formhaus.compose.theme.FormhausTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

private val types =
    listOf(
        "text",
        "email",
        "phone",
        "number",
        "password",
        "textarea",
        "autocomplete",
        "select",
        "radio",
        "multiselect",
        "date",
        "time",
        "datetime",
        "file",
        "scale",
        "rating",
        "checkbox",
        "switch",
    )

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w480dp-h3200dp-mdpi")
class LayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(
        form: FormModel,
        theme: FormhausTheme,
    ): MeasuredFrames {
        val log = MeasuredFrames()
        compose.setContent {
            CompositionLocalProvider(LocalMeasuredFrames provides log) {
                Themed(theme, Modifier.width(480.dp)) { FieldList(form) }
            }
        }
        compose.waitForIdle()
        return log
    }

    private fun near(
        expected: Float,
        actual: Float,
    ) = abs(expected - actual) < 0.5f

    @Test
    fun everyFieldTypeKeepsTheLabelControlAndGapSpacing() {
        val fields = types.map { """{"key": "$it", "type": "$it", "label": "$it", "options": [{"value": "a", "label": "A"}]}""" }
        val log = render(model(definition(*fields.toTypedArray())), FormhausTheme.pointer).frames
        val tokens = FormhausTokens(FormhausTheme.pointer, dark = false, fontScale = 1f, reduceMotion = false)
        for (type in types) {
            val field = requireNotNull(log["$type/field"]) { "no frames for $type" }
            val control = requireNotNull(log["$type/control"]) { "no control for $type" }
            val label = log["$type/label"] ?: continue
            assertTrue("label inset of $type", near(field.top, label.top))
            assertTrue("label to control of $type", near(tokens.spacing.s3.value, control.top - label.bottom))
        }
        for ((above, below) in types.zipWithNext()) {
            val gap = log.getValue("$below/field").top - log.getValue("$above/field").bottom
            assertTrue("gap between $above and $below: $gap", near(tokens.spacing.gap.value, gap))
        }
    }

    @Test
    fun everyControlIsAtLeastOneHitTargetTallAndEveryFieldHasASpokenName() {
        val fields =
            types.map {
                """{"key": "$it", "type": "$it", "label": "Label $it", "validation": {"required": true},
                    "options": [{"value": "a", "label": "A"}, {"value": "b", "label": "B"}]}"""
            }
        val form = model(definition(*fields.toTypedArray()))
        val log = render(form, FormhausTheme.touch).frames
        val minimum = FormhausTheme.touch.controls.hitTarget.value - 0.5f
        for (field in form.structure.visibleFields) {
            assertEquals("Label ${field.key}, ${form.label("required")}", FieldContext(field, form).accessibilityName)
            val control = requireNotNull(log["${field.key}/control"]) { "no control for ${field.key}" }
            assertTrue("${field.key} $control", control.height >= minimum && control.width >= minimum)
        }
    }
}
