package dev.formhaus.compose.system

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.formhausFocus
import dev.formhaus.compose.components.marked
import dev.formhaus.compose.fields.InputKind
import dev.formhaus.compose.fields.numberText
import dev.formhaus.compose.fields.numberValue
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.form.formhausScrollAnchor
import dev.formhaus.compose.model.formhausTestId
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.json.JsonValue

private const val TEXTAREA_LINES = 4

@Composable
internal fun SystemTextField(context: FieldContext) {
    val tokens = LocalFormhausTokens.current
    val kind = InputKind.of(context.field)
    val number = context.field.type == "number"
    val area = context.field.type == "textarea"
    var draft by remember { mutableStateOf("") }
    val requester = remember { FocusRequester() }
    val notes = context.error?.takeIf { it.isNotEmpty() } ?: context.helperText
    val prompt = context.placeholder ?: context.field.mask?.stringValue
    OutlinedTextField(
        if (number) numberText(context, draft) else context.text,
        { typed ->
            if (number) {
                draft = typed
                context.setValue(numberValue(typed))
            } else {
                context.setValue(JsonValue.String(typed))
            }
        },
        Modifier
            .fillMaxWidth()
            .formhausFocus(context, requester)
            .semantics { contentDescription = context.accessibilityName }
            .formhausTestId(context.testId)
            .formhausScrollAnchor(context),
        enabled = !context.isLocked,
        label = { Text(tokens.marked(context.label, context.isRequired)) },
        placeholder = prompt?.let { { Text(it) } },
        trailingIcon = if (context.loading) ({ CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }) else null,
        supportingText = notes?.let { { Text(it) } },
        isError = context.isInvalid,
        visualTransformation = if (kind == InputKind.PASSWORD) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = kind.keyboardOptions(context.model.structure.isLastStep),
        keyboardActions = KeyboardActions { context.performPrimaryAction() },
        singleLine = !area,
        minLines = if (area) TEXTAREA_LINES else 1,
    )
}
