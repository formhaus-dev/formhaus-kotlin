package dev.formhaus.compose.fields

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle

private const val DASH = 4f
private const val GAP = 3f

@Composable
internal fun BuiltInField(context: FieldContext) {
    when (context.field.type) {
        "text", "email", "phone", "number", "password" -> TextInputField(context)
        "textarea" -> TextAreaField(context)
        "autocomplete" -> AutocompleteField(context)
        "select" -> SelectField(context)
        "radio" -> RadioField(context)
        "multiselect" -> MultiselectField(context)
        "checkbox" -> CheckboxField(context)
        "switch" -> SwitchField(context)
        "date", "time", "datetime" -> DateField(context)
        "file" -> FileField(context)
        "scale" -> ScaleField(context)
        "rating" -> RatingField(context)
        "hidden" -> Unit
        else -> UnsupportedField(context.field.type)
    }
}

@Composable
internal fun UnsupportedField(type: String) {
    val tokens = LocalFormhausTokens.current
    val border = tokens.colors.border
    val radius = tokens.radii.control
    Text(
        "Unsupported field type: $type",
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val width = 1.dp.toPx()
                drawRoundRect(
                    border,
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH * width, GAP * width))),
                )
            }.padding(tokens.spacing.s5),
        style = tokens.textStyle(TextRole.helper),
    )
}
