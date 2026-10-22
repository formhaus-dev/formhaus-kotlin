package dev.formhaus.compose.fields

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import dev.formhaus.compose.components.FieldShell
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.core.json.JsonValue

@Composable
public fun TextInputField(context: FieldContext) {
    val kind = InputKind.of(context.field)
    var draft by remember { mutableStateOf("") }
    val number = context.field.type == "number"
    val text = if (number) numberText(context, draft) else context.text
    val type = kind.contentType
    FieldShell(context) {
        TextInput(
            context,
            text,
            { typed ->
                if (number) {
                    draft = typed
                    context.setValue(numberValue(typed))
                } else {
                    context.setValue(JsonValue.String(typed))
                }
            },
            context.placeholder ?: context.field.mask?.stringValue,
            TextInputLook(
                keyboard = kind.keyboardOptions(context.model.structure.isLastStep),
                transformation = if (kind == InputKind.PASSWORD) PasswordVisualTransformation() else VisualTransformation.None,
                inputModifier = if (type != null) Modifier.semantics { contentType = type } else Modifier,
            ),
        )
    }
}

private fun numberText(
    context: FieldContext,
    draft: String,
): String {
    if (draft.isNotEmpty()) {
        val parsed = parseNumber(draft)
        if (parsed != null && context.value == JsonValue.Number(parsed)) return draft
    }
    return context.text
}

private val numeral = Regex("""[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")

internal fun parseNumber(text: String): Double? =
    text
        .trim()
        .replace(',', '.')
        .takeIf(numeral::matches)
        ?.toDoubleOrNull()
        ?.takeIf { it.isFinite() }

internal fun numberValue(text: String): JsonValue? {
    if (text.isEmpty()) return JsonValue.Null
    return parseNumber(text)?.let { JsonValue.Number(it) } ?: JsonValue.String(text)
}
