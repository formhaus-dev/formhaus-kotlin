package dev.formhaus.compose.containers

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.ChoiceIndicator
import dev.formhaus.compose.components.ColumnGrid
import dev.formhaus.compose.components.FieldError
import dev.formhaus.compose.components.GridSpec
import dev.formhaus.compose.components.IndicatorShape
import dev.formhaus.compose.components.focusRing
import dev.formhaus.compose.components.formhausFocus
import dev.formhaus.compose.components.marked
import dev.formhaus.compose.fields.ChoiceSelection
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.form.formhausScrollAnchor
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.formhausTestId
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.model.FormField

private const val CELL_MINIMUM = 60

@Composable
internal fun MatrixRow(
    field: FormField,
    model: FormModel,
    wide: Boolean,
    last: Boolean,
) {
    val tokens = LocalFormhausTokens.current
    val context = FieldContext(field, model)
    val requester = remember { FocusRequester() }
    val columns = context.options
    val shape = if (field.type == "radio") IndicatorShape.RADIO else IndicatorShape.CHECKBOX
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (context.focused) tokens.colors.backgroundHover else Color.Transparent)
            .formhausFocus(context, requester)
            .semantics { contentDescription = context.accessibilityName }
            .formhausTestId(context.testId)
            .formhausScrollAnchor(context),
    ) {
        if (wide) {
            MatrixLine(columns.size) {
                MatrixHeading(context, Modifier.padding(vertical = tokens.spacing.s5).padding(end = tokens.spacing.s5))
                columns.forEach { MatrixCell(context, it, shape, caption = false) }
            }
        } else {
            Column(Modifier.padding(bottom = tokens.spacing.s5), verticalArrangement = Arrangement.spacedBy(tokens.spacing.s3)) {
                MatrixHeading(context, Modifier.padding(top = tokens.spacing.s5))
                val spec =
                    GridSpec(
                        maxOf(1, columns.size),
                        (CELL_MINIMUM * maxOf(1f, tokens.scale)).dp,
                        0.dp,
                        tokens.spacing.s1,
                        fraction = 1f,
                        balanced = true,
                    )
                ColumnGrid(tokens.spacing.s1, spec::columnCount) { columns.forEach { MatrixCell(context, it, shape, caption = true) } }
            }
        }
        if (!last) HorizontalDivider(thickness = 1.dp, color = tokens.colors.border)
    }
}

@Composable
private fun MatrixHeading(
    context: FieldContext,
    modifier: Modifier,
) {
    val tokens = LocalFormhausTokens.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(tokens.spacing.s1)) {
        Text(
            tokens.marked(context.label, context.isRequired),
            style = tokens.textStyle(TextRole.option).copy(fontWeight = FontWeight.Medium),
        )
        context.helperText?.let { Text(it, style = tokens.textStyle(TextRole.helper)) }
        context.error?.takeIf { it.isNotEmpty() }?.let { FieldError(it) }
    }
}

@Composable
private fun MatrixCell(
    context: FieldContext,
    option: FieldOption,
    shape: IndicatorShape,
    caption: Boolean,
) {
    val tokens = LocalFormhausTokens.current
    val value = option.value.orEmpty()
    val selection = ChoiceSelection(context)
    val selected = selection.isSelected(value)
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Column(
        Modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected,
                source,
                null,
                enabled = !context.isLocked,
                role = if (shape == IndicatorShape.RADIO) Role.RadioButton else Role.Checkbox,
            ) { selection.choose(value) }
            .semantics { contentDescription = option.label ?: value }
            .formhausTestId(context.optionTestId(value))
            .padding(vertical = if (caption) tokens.spacing.s2 else 0.dp),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.s1, if (caption) Alignment.Top else Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChoiceIndicator(shape, selected, Modifier.focusRing(focused, tokens.radii.indicator), context.isInvalid)
        if (caption) Text(option.label ?: value, textAlign = TextAlign.Center, style = tokens.textStyle(TextRole.caption))
    }
}
