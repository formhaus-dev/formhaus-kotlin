package dev.formhaus.compose.fields

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import dev.formhaus.compose.components.LoadingIndicator
import dev.formhaus.compose.components.Placeholder
import dev.formhaus.compose.components.formhausFocus
import dev.formhaus.compose.components.inputChrome
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle

@Composable
internal fun TextInput(
    context: FieldContext,
    text: String,
    onText: (String) -> Unit,
    placeholder: String?,
    look: TextInputLook,
    source: MutableInteractionSource = remember { MutableInteractionSource() },
    alignment: Alignment.Vertical = Alignment.CenterVertically,
) {
    val tokens = LocalFormhausTokens.current
    val focused by source.collectIsFocusedAsState()
    val requester = remember { FocusRequester() }
    val locked = context.isLocked
    Row(
        Modifier.inputChrome(focused, context.isInvalid, locked),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s3),
        verticalAlignment = alignment,
    ) {
        BasicTextField(
            text,
            onText,
            look.inputModifier
                .weight(1f)
                .formhausFocus(context, requester)
                .semantics { contentDescription = context.accessibilityName },
            enabled = !locked,
            textStyle =
                tokens.textStyle(TextRole.body, colored = false).copy(color = if (locked) tokens.colors.textMuted else tokens.colors.text),
            keyboardOptions = look.keyboard,
            keyboardActions = KeyboardActions { context.performPrimaryAction() },
            singleLine = look.singleLine,
            minLines = look.minLines,
            visualTransformation = look.transformation,
            interactionSource = source,
            cursorBrush = SolidColor(tokens.colors.primary),
        ) { inner -> Placeholder(placeholder, text.isEmpty()) { Box { inner() } } }
        LoadingIndicator(context.loading)
    }
}

internal class TextInputLook(
    val keyboard: KeyboardOptions = KeyboardOptions.Default,
    val transformation: VisualTransformation = VisualTransformation.None,
    val singleLine: Boolean = true,
    val minLines: Int = 1,
    val inputModifier: Modifier = Modifier,
)
