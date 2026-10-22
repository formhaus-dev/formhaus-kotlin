package dev.formhaus.compose.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.formhaus.compose.components.FormhausIcons
import dev.formhaus.compose.components.OnFormhausFocus
import dev.formhaus.compose.components.marked
import dev.formhaus.compose.form.GroupContext
import dev.formhaus.compose.form.GroupItemContext
import dev.formhaus.compose.form.LocalFormComponents
import dev.formhaus.compose.form.formhausScrollAnchor
import dev.formhaus.compose.model.FieldState
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.renderer.isRequired

@Composable
internal fun SystemGroup(
    context: GroupContext,
    state: FieldState,
) {
    val itemSlot = LocalFormComponents.current.slots.groupItem
    val requester = remember { FocusRequester() }
    val required = context.group.isRequired
    OnFormhausFocus(state, context.model, context.disabled) { if (context.canAdd) requester.requestFocus() }
    Column(
        Modifier.fillMaxWidth().formhausScrollAnchor(context.group.key),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            LocalFormhausTokens.current.marked(context.label, required),
            Modifier.semantics {
                heading()
                contentDescription = if (required) "${context.label}, ${context.model.label("required")}" else context.label
            },
            style = MaterialTheme.typography.titleMedium,
        )
        context.helperText?.takeIf { it.isNotEmpty() }?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        context.error?.takeIf { it.isNotEmpty() }?.let { SystemErrorText(it) }
        for (item in context.items) key(item.id) { itemSlot?.invoke(item) ?: SystemGroupItem(item) }
        if (context.canAdd) {
            OutlinedButton(context.add, Modifier.focusRequester(requester), enabled = !context.disabled) {
                Icon(FormhausIcons.plus, null, Modifier.size(ButtonDefaults.IconSize))
                Text(context.addLabel, Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        }
    }
}

@Composable
private fun SystemGroupItem(item: GroupItemContext) {
    val requester = remember { FocusRequester() }
    OnFormhausFocus(item.removeFocus, item.model, item.disabled) { if (item.canRemove) requester.requestFocus() }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                if (item.canRemove) {
                    TextButton(
                        item.requestRemove,
                        Modifier.focusRequester(requester).semantics { contentDescription = item.removeName },
                        enabled = !item.disabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(item.removeLabel) }
                }
            }
            item.content()
        }
    }
}
