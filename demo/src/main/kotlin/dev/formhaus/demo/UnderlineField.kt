package dev.formhaus.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.FieldShell
import dev.formhaus.compose.components.formhausFocus
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.form.formhausScrollAnchor
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.json.JsonValue

@Composable
fun UnderlineField(context: FieldContext) {
    val tokens = LocalFormhausTokens.current
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    FieldShell(context, Modifier.formhausScrollAnchor(context)) {
        Column {
            BasicTextField(
                value = context.text,
                onValueChange = { context.setValue(JsonValue.String(it)) },
                enabled = !context.isLocked,
                singleLine = true,
                textStyle = tokens.textStyle(TextRole.body).copy(color = tokens.colors.text),
                cursorBrush = SolidColor(tokens.colors.primary),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .formhausFocus(context, requester)
                        .onFocusChanged { focused = it.hasFocus }
                        .semantics { contentDescription = context.accessibilityName },
            )
            val line = if (focused) tokens.colors.primary else tokens.colors.border
            Box(Modifier.fillMaxWidth().height(if (focused) 2.dp else 1.dp).background(line))
        }
    }
}
