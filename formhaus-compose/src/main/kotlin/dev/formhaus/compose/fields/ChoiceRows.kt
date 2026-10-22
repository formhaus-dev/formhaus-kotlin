package dev.formhaus.compose.fields

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.formhaus.compose.components.FieldError
import dev.formhaus.compose.components.FieldHeader
import dev.formhaus.compose.components.FieldShellVariant
import dev.formhaus.compose.components.formhausFocus
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.form.FormComponents
import dev.formhaus.compose.form.formhausScrollAnchor
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.formhausTestId
import dev.formhaus.compose.model.options
import dev.formhaus.compose.theme.FormhausTokens
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.model.FormField

private const val SPLIT_OPTIONS = 48

internal class ChoicePlan(
    val arrangement: ChoiceArrangement,
    val columns: Int,
    val count: Int,
) {
    val rows: Int get() = (count + columns - 1) / columns

    fun range(row: Int): IntRange = row * columns until minOf(count, (row + 1) * columns)
}

internal fun choicePlan(
    field: FormField,
    model: FormModel,
    components: FormComponents,
    tokens: FormhausTokens,
    width: Dp,
): ChoicePlan? {
    if (field.type != "radio" && field.type != "multiselect") return null
    if (!components.builtIns || components.system || components.fields.containsKey(field.type)) return null
    val options = model.options(field.key) ?: field.options.orEmpty()
    if (options.size <= SPLIT_OPTIONS) return null
    return when (val arrangement = choiceArrangement(field, options)) {
        ChoiceArrangement.INLINE -> {
            null
        }

        ChoiceArrangement.LIST -> {
            ChoicePlan(arrangement, 1, options.size)
        }

        ChoiceArrangement.PICTURES -> {
            ChoicePlan(arrangement, tokens.pictureGrid(field.columns).columnCount(width, options.size), options.size)
        }
    }
}

internal fun FormhausTokens.choiceRowGap(plan: ChoicePlan): Dp =
    if (plan.arrangement ==
        ChoiceArrangement.PICTURES
    ) {
        spacing.s5
    } else {
        spacing.s3
    }

@Composable
internal fun ChoiceHeader(
    field: FormField,
    model: FormModel,
) {
    val context = FieldContext(field, model)
    val error = context.error?.takeIf { it.isNotEmpty() }
    PrefetchImages(context.options)
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { if (error != null) error(error) }
            .formhausTestId(context.testId)
            .formhausScrollAnchor(context),
    ) { FieldHeader(context, FieldShellVariant.GROUP, context.helperText) }
}

@Composable
internal fun ChoiceRow(
    field: FormField,
    model: FormModel,
    plan: ChoicePlan,
    row: Int,
) {
    val context = FieldContext(field, model)
    val requester = remember { FocusRequester() }
    ChoiceScope(context) { actions ->
        val focus = if (row == 0) Modifier.formhausFocus(context, requester) else Modifier
        ChoiceOptions(
            context,
            field.indicatorShape,
            plan.arrangement,
            plan.range(row),
            { _, _ -> plan.columns },
            actions,
            focus.selectableGroup(),
        )
    }
}

@Composable
internal fun ChoiceError(
    field: FormField,
    model: FormModel,
) {
    val error = model.field(field.key).error?.takeIf { it.isNotEmpty() } ?: return
    FieldError(error, Modifier.padding(top = LocalFormhausTokens.current.spacing.s3))
}
