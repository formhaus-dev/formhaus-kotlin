package dev.formhaus.compose.system

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import dev.formhaus.compose.components.formhausFocus
import dev.formhaus.compose.components.marked
import dev.formhaus.compose.fields.ChoiceSelection
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.form.formhausScrollAnchor
import dev.formhaus.compose.model.formhausTestId
import dev.formhaus.compose.theme.LocalFormhausTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SystemSelect(context: FieldContext) {
    val tokens = LocalFormhausTokens.current
    val requester = remember { FocusRequester() }
    var open by remember { mutableStateOf(false) }
    val selection = ChoiceSelection(context)
    val chosen = selection.values.firstOrNull()
    val selected = context.options.firstOrNull { it.value == chosen }?.let { it.label ?: it.value } ?: chosen
    val notes = context.error?.takeIf { it.isNotEmpty() } ?: context.helperText
    val enabled = !context.isLocked
    ExposedDropdownMenuBox(
        open,
        { open = it && enabled },
        Modifier
            .fillMaxWidth()
            .formhausTestId(context.testId)
            .formhausScrollAnchor(context),
    ) {
        OutlinedTextField(
            selected.orEmpty(),
            {},
            Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .formhausFocus(context, requester)
                .semantics { contentDescription = context.accessibilityName },
            enabled = enabled,
            readOnly = true,
            label = { Text(tokens.marked(context.label, context.isRequired)) },
            placeholder = { Text(context.placeholder ?: context.text("select")) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            supportingText = notes?.let { { Text(it) } },
            isError = context.isInvalid,
            singleLine = true,
        )
        ExposedDropdownMenu(open, { open = false }) {
            for (option in context.options) {
                val value = option.value.orEmpty()
                DropdownMenuItem(
                    { Text(option.label ?: value) },
                    {
                        open = false
                        selection.choose(value)
                    },
                    Modifier.formhausTestId(context.optionTestId(value)),
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
