package dev.formhaus.compose.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.formhausFocus
import dev.formhaus.compose.components.marked
import dev.formhaus.compose.fields.ChoiceSelection
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.form.formhausScrollAnchor
import dev.formhaus.compose.model.formhausTestId
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.json.JsonValue

private val rowHeight = 48.dp

@Composable
internal fun SystemChoiceList(context: FieldContext) {
    val requester = remember { FocusRequester() }
    val selection = ChoiceSelection(context)
    val multiple = selection.multiple
    SystemBlock(context) {
        Column(Modifier.formhausFocus(context, requester).selectableGroup()) {
            for (option in context.options) {
                val value = option.value.orEmpty()
                val selected = selection.isSelected(value)
                ChoiceRow(
                    Modifier
                        .selectable(selected, enabled = !context.isLocked, role = if (multiple) Role.Checkbox else Role.RadioButton) {
                            selection.choose(value)
                        }.formhausTestId(context.optionTestId(value)),
                ) {
                    if (multiple) {
                        Checkbox(selected, null, enabled = !context.isLocked)
                    } else {
                        RadioButton(selected, null, enabled = !context.isLocked)
                    }
                    Text(option.label ?: value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
internal fun SystemToggle(
    context: FieldContext,
    switch: Boolean,
) {
    val requester = remember { FocusRequester() }
    val checked = context.value?.boolValue == true
    val enabled = !context.isLocked
    Column(
        Modifier
            .fillMaxWidth()
            .formhausTestId(context.testId)
            .formhausScrollAnchor(context),
    ) {
        ChoiceRow(
            Modifier
                .formhausFocus(context, requester)
                .toggleable(checked, enabled = enabled, role = if (switch) Role.Switch else Role.Checkbox) {
                    context.setValue(JsonValue.Bool(it))
                }.semantics { contentDescription = context.accessibilityName },
        ) {
            if (!switch) Checkbox(checked, null, enabled = enabled)
            Text(
                LocalFormhausTokens.current.marked(context.label, context.isRequired),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (switch) Switch(checked, null, enabled = enabled)
        }
        SystemNotes(context)
    }
}

@Composable
private fun ChoiceRow(
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = rowHeight),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
