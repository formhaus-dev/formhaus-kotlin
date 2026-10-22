package dev.formhaus.compose.fields

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.FieldShell
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.renderer.clampedInt

private const val MAX_ROWS = 40
private const val DEFAULT_ROWS = 3
private const val LINE = 1.5f

@Composable
public fun TextAreaField(context: FieldContext) {
    val tokens = LocalFormhausTokens.current
    val rows = clampedInt(context.field.rows?.numberValue, 1..MAX_ROWS) ?: DEFAULT_ROWS
    val minHeight = maxOf(tokens.controls.textareaHeight - tokens.spacing.s4 * 2 - 2.dp, (tokens.typography.body.value * LINE * rows).dp)
    FieldShell(context) {
        TextInput(
            context,
            context.text,
            { context.setValue(JsonValue.String(it)) },
            context.placeholder,
            TextInputLook(singleLine = false, minLines = rows, inputModifier = Modifier.heightIn(min = minHeight)),
            alignment = Alignment.Top,
        )
    }
}
