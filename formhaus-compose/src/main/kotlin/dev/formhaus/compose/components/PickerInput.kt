package dev.formhaus.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.json.JsonValue

@Composable
internal fun PickerInput(
    context: FieldContext,
    active: Boolean,
    clearable: Boolean,
    cleared: JsonValue,
    valueText: String,
    open: () -> Unit,
    below: @Composable () -> Unit = {},
    label: @Composable () -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val requester = remember { FocusRequester() }
    FieldShell(context) {
        Row(
            Modifier.inputChrome(focused || active, context.isInvalid, context.isLocked),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .formhausFocus(context, requester)
                    .focusable(!context.isLocked, source)
                    .clickable(source, null, !context.isLocked, role = Role.Button, onClick = open)
                    .semantics {
                        contentDescription = context.accessibilityName
                        stateDescription = valueText
                    },
            ) {
                Box(Modifier.clearAndSetSemantics {}) { label() }
            }
            FieldAccessories(context, clearable) { context.setValue(cleared) }
        }
        below()
    }
}
