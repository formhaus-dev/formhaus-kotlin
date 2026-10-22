package dev.formhaus.compose.containers

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.FieldError
import dev.formhaus.compose.components.FieldLabel
import dev.formhaus.compose.components.FormButton
import dev.formhaus.compose.components.FormButtonVariant
import dev.formhaus.compose.components.FormhausIcons
import dev.formhaus.compose.components.OnFormhausFocus
import dev.formhaus.compose.components.controlShape
import dev.formhaus.compose.form.GroupContext
import dev.formhaus.compose.form.GroupItemContext
import dev.formhaus.compose.form.LocalFormComponents
import dev.formhaus.compose.form.formhausScrollAnchor
import dev.formhaus.compose.model.FieldState
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.renderer.isRequired

@Composable
internal fun DefaultGroup(
    context: GroupContext,
    state: FieldState,
) {
    val tokens = LocalFormhausTokens.current
    val itemSlot = LocalFormComponents.current.slots.groupItem
    val requester = remember { FocusRequester() }
    OnFormhausFocus(state, context.model, context.disabled) { if (context.canAdd) requester.requestFocus() }
    Column(Modifier.fillMaxWidth().formhausScrollAnchor(context.group.key)) {
        FieldLabel(context.label, context.group.isRequired, context.model.label("required"), heading = true)
        context.helperText?.takeIf { it.isNotEmpty() }?.let {
            Spacer(Modifier.height(tokens.spacing.s1))
            Text(it, style = tokens.textStyle(TextRole.helper))
        }
        context.error?.takeIf { it.isNotEmpty() }?.let {
            Spacer(Modifier.height(tokens.spacing.s1))
            FieldError(it)
        }
        Spacer(Modifier.height(tokens.spacing.s7))
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.s7)) {
            for (item in context.items) key(item.id) { itemSlot?.invoke(item) ?: DefaultGroupItem(item) }
            if (context.canAdd) {
                FormButton(
                    context.addLabel,
                    FormButtonVariant.SECONDARY,
                    context.add,
                    Modifier.focusRequester(requester),
                    enabled = !context.disabled,
                    icon = FormhausIcons.plus,
                )
            }
        }
    }
}

@Composable
internal fun DefaultGroupItem(context: GroupItemContext) {
    val tokens = LocalFormhausTokens.current
    val requester = remember { FocusRequester() }
    OnFormhausFocus(context.removeFocus, context.model, context.disabled) { if (context.canRemove) requester.requestFocus() }
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, tokens.colors.border, tokens.controlShape)
            .padding(start = tokens.spacing.cardPadding, end = tokens.spacing.cardPadding, bottom = tokens.spacing.cardPadding)
            .padding(top = tokens.spacing.s7),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(context.title, Modifier.weight(1f).semantics { heading() }, style = tokens.textStyle(TextRole.itemTitle))
            if (context.canRemove) {
                FormButton(
                    context.removeLabel,
                    FormButtonVariant.DANGER,
                    context.requestRemove,
                    Modifier
                        .offset(x = tokens.spacing.s4)
                        .focusRequester(requester)
                        .semantics { contentDescription = context.removeName },
                    enabled = !context.disabled,
                    compact = true,
                )
            }
        }
        Spacer(Modifier.height(tokens.spacing.s7))
        context.content()
    }
}
