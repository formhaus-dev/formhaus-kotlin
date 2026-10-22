package dev.formhaus.compose.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.FieldShell
import dev.formhaus.compose.components.controlShape
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.RaisedShadow
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FieldOption

private const val SUGGESTION_LIMIT = 6

@Composable
public fun AutocompleteField(context: FieldContext) {
    val tokens = LocalFormhausTokens.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    FieldShell(context) {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.s1)) {
            TextInput(
                context,
                context.text,
                { context.setValue(JsonValue.String(it)) },
                context.placeholder,
                TextInputLook(keyboard = KeyboardOptions(autoCorrectEnabled = false)),
                source,
            )
            val matches = suggestions(context)
            if (focused && matches.isNotEmpty()) SuggestionList(matches) { context.setValue(JsonValue.String(it)) }
        }
    }
}

internal fun suggestions(context: FieldContext): List<FieldOption> {
    val query = context.text.trim()
    if (query.isEmpty()) return emptyList()
    val matches =
        context.options.filter { option ->
            option.label.orEmpty().contains(query, ignoreCase = true) || option.value.orEmpty().contains(query, ignoreCase = true)
        }
    if (matches.size == 1 && matches[0].value == context.text) return emptyList()
    return matches.take(SUGGESTION_LIMIT)
}

@Composable
private fun SuggestionList(
    options: List<FieldOption>,
    pick: (String) -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    val shape = tokens.controlShape
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(RaisedShadow.blur, shape, ambientColor = tokens.colors.shadow, spotColor = tokens.colors.shadow)
            .background(tokens.colors.background, shape)
            .border(1.dp, tokens.colors.border, shape)
            .padding(vertical = tokens.spacing.s1),
    ) {
        for (option in options) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = tokens.controls.hitTarget)
                    .clickable(role = Role.Button) { pick(option.value.orEmpty()) }
                    .padding(horizontal = tokens.spacing.s6),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(option.label ?: option.value.orEmpty(), style = tokens.textStyle(TextRole.option))
            }
        }
    }
}
