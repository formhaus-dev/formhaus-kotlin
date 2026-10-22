package dev.formhaus.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.FieldShell
import dev.formhaus.compose.components.formhausFocus
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.json.JsonValue

@Composable
fun UnderlineField(context: FieldContext) {
    FieldShell(context) { UnderlineInput(context, LocalFormhausTokens.current.textStyle(TextRole.body)) }
}

@Composable
internal fun UnderlineInput(
    context: FieldContext,
    style: TextStyle,
) {
    val colors = LocalFormhausTokens.current.colors
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    Column {
        BasicTextField(
            value = context.text,
            onValueChange = { context.setValue(JsonValue.String(it)) },
            enabled = !context.isLocked,
            singleLine = true,
            textStyle = style.copy(color = colors.text),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType(context)),
            keyboardActions = KeyboardActions { context.performPrimaryAction() },
            decorationBox = { input ->
                Box {
                    context.placeholder?.takeIf { context.text.isEmpty() }?.let { Text(it, style = style.copy(color = colors.control)) }
                    input()
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .formhausFocus(context, requester)
                    .onFocusChanged { focused = it.hasFocus }
                    .semantics { contentDescription = context.accessibilityName },
        )
        val line =
            when {
                focused -> colors.primary
                context.isInvalid -> colors.error
                else -> colors.inputBorder
            }
        Box(Modifier.fillMaxWidth().height(if (focused) 2.dp else 1.5.dp).background(line, CircleShape))
    }
}

private fun keyboardType(context: FieldContext): KeyboardType =
    when {
        context.field.type == "email" -> KeyboardType.Email
        context.field.inputMode?.stringValue == "url" -> KeyboardType.Uri
        else -> KeyboardType.Text
    }
