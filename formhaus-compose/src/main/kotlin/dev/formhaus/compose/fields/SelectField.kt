package dev.formhaus.compose.fields

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import dev.formhaus.compose.components.FieldShell
import dev.formhaus.compose.components.FormhausIcons
import dev.formhaus.compose.components.LoadingIndicator
import dev.formhaus.compose.components.formhausFocus
import dev.formhaus.compose.components.inputChrome
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.json.JsonValue

@Composable
public fun SelectField(context: FieldContext) {
    val tokens = LocalFormhausTokens.current
    val colors = tokens.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val requester = remember { FocusRequester() }
    var open by remember { mutableStateOf(false) }
    val selected = selectedLabel(context)
    FieldShell(context) {
        Box {
            Row(
                Modifier
                    .inputChrome(focused || open, context.isInvalid, context.isLocked)
                    .formhausFocus(context, requester)
                    .focusable(!context.isLocked, source)
                    .clickable(source, null, !context.isLocked, role = Role.DropdownList) { open = true }
                    .semantics {
                        contentDescription = context.accessibilityName
                        stateDescription = selected.orEmpty()
                    },
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selected ?: context.placeholder.orEmpty(),
                    Modifier.weight(1f).clearAndSetSemantics {},
                    color =
                        when {
                            selected == null -> colors.control
                            context.isLocked -> colors.textMuted
                            else -> colors.text
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = tokens.textStyle(TextRole.body, colored = false),
                )
                LoadingIndicator(context.loading)
                Icon(FormhausIcons.arrowDropDown, null, Modifier.size(tokens.controls.indicator * 4 / 3), tint = colors.textMuted)
            }
            SelectMenu(context, open) { open = false }
        }
    }
}

private fun selectedLabel(context: FieldContext): String? {
    if (context.value == null || context.text.isEmpty()) return null
    return context.options.firstOrNull { it.value == context.text }?.label ?: context.text
}

@Composable
private fun SelectMenu(
    context: FieldContext,
    open: Boolean,
    close: () -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    val colors = tokens.colors
    DropdownMenu(open, close, containerColor = colors.background) {
        for (option in context.options) {
            val value = option.value.orEmpty()
            DropdownMenuItem(
                { Text(option.label ?: value, style = tokens.textStyle(TextRole.option)) },
                {
                    close()
                    context.setValue(JsonValue.String(value))
                },
                trailingIcon =
                    if (value == context.text) {
                        { Icon(FormhausIcons.check, null, Modifier.size(tokens.controls.indicator), tint = colors.primary) }
                    } else {
                        null
                    },
            )
        }
    }
}
