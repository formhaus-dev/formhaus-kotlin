package dev.formhaus.compose.fields

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.ChoiceIndicator
import dev.formhaus.compose.components.FieldShell
import dev.formhaus.compose.components.FieldShellVariant
import dev.formhaus.compose.components.IndicatorShape
import dev.formhaus.compose.components.LoadingIndicator
import dev.formhaus.compose.components.alignedToText
import dev.formhaus.compose.components.focusRing
import dev.formhaus.compose.components.formhausFocus
import dev.formhaus.compose.components.textBottom
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.json.JsonValue

private val SWITCH_HEIGHT = 32.dp

@Composable
public fun CheckboxField(context: FieldContext) {
    val requester = remember { FocusRequester() }
    FieldShell(context, variant = FieldShellVariant.INLINE) {
        FormhausCheckbox(
            context.value?.boolValue == true,
            { context.setValue(JsonValue.Bool(it)) },
            context.label,
            Modifier
                .formhausFocus(context, requester)
                .semantics { contentDescription = context.accessibilityName },
            invalid = context.isInvalid,
            enabled = !context.isLocked,
        )
    }
}

@Composable
public fun FormhausCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    invalid: Boolean = false,
    enabled: Boolean = true,
) {
    val tokens = LocalFormhausTokens.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = tokens.controls.hitTarget)
            .focusable(enabled, source)
            .toggleable(checked, source, null, enabled, Role.Checkbox, onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChoiceIndicator(IndicatorShape.CHECKBOX, checked, alignedToText().focusRing(focused, tokens.radii.indicator), invalid)
        Text(label, Modifier.weight(1f).alignBy(FirstBaseline).textBottom(), style = tokens.textStyle(TextRole.option))
    }
}

@Composable
public fun SwitchField(context: FieldContext) {
    val tokens = LocalFormhausTokens.current
    val colors = tokens.colors
    val requester = remember { FocusRequester() }
    val checked = context.value?.boolValue == true
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val border = if (context.isInvalid) colors.error else colors.control
    FieldShell(context, variant = FieldShellVariant.ROW) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = tokens.controls.hitTarget)
                .formhausFocus(context, requester)
                .focusable(!context.isLocked, source)
                .toggleable(checked, source, null, !context.isLocked, Role.Switch) {
                    context.setValue(JsonValue.Bool(it))
                }.semantics { contentDescription = context.accessibilityName },
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(context.label, Modifier.weight(1f).textBottom(), style = tokens.textStyle(TextRole.option))
            LoadingIndicator(context.loading)
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Switch(
                    checked,
                    null,
                    Modifier.focusRing(focused, SWITCH_HEIGHT / 2),
                    enabled = !context.isLocked,
                    colors =
                        SwitchDefaults.colors(
                            checkedTrackColor = colors.primary,
                            checkedThumbColor = colors.onPrimary,
                            checkedBorderColor = colors.primary,
                            uncheckedTrackColor = colors.backgroundDisabled,
                            uncheckedThumbColor = colors.control,
                            uncheckedBorderColor = border,
                            disabledCheckedTrackColor = colors.primary,
                            disabledCheckedThumbColor = colors.onPrimary,
                            disabledUncheckedTrackColor = colors.backgroundDisabled,
                            disabledUncheckedThumbColor = colors.control,
                            disabledUncheckedBorderColor = border,
                        ),
                )
            }
        }
    }
}
