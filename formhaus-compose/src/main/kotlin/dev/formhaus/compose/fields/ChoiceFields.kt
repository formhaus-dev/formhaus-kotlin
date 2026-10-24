package dev.formhaus.compose.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.ColumnGrid
import dev.formhaus.compose.components.FieldShell
import dev.formhaus.compose.components.FieldShellVariant
import dev.formhaus.compose.components.GridSpec
import dev.formhaus.compose.components.ImageViewer
import dev.formhaus.compose.components.IndicatorShape
import dev.formhaus.compose.components.OptionImages
import dev.formhaus.compose.components.focusedOption
import dev.formhaus.compose.components.formhausOptionsFocus
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.form.numberText
import dev.formhaus.compose.theme.FormhausTokens
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.model.FormField
import dev.formhaus.core.renderer.safeImage

private const val PREFETCH_LIMIT = 64
private const val LARGE_TYPE = 1.6f
private const val PICTURE_MINIMUM = 160

@Composable
public fun RadioField(context: FieldContext) {
    ChoiceList(context, IndicatorShape.RADIO)
}

@Composable
public fun MultiselectField(context: FieldContext) {
    ChoiceList(context, IndicatorShape.CHECKBOX)
}

public class ChoiceSelection(
    private val context: FieldContext,
) {
    public val multiple: Boolean get() = context.field.type == "multiselect"

    public val values: List<String> get() = values(context.value, multiple)

    public fun isSelected(value: String): Boolean = value in values

    public fun choose(value: String) {
        if (!multiple) return context.commit(JsonValue.String(value))
        val current = values
        val next = if (value in current) current - value else current + value
        context.setValue(JsonValue.Array(next.map { JsonValue.String(it) }))
    }

    internal companion object {
        fun values(
            value: JsonValue?,
            multiple: Boolean,
        ): List<String> {
            if (multiple) return value?.arrayValue?.mapNotNull { it.stringValue }.orEmpty()
            if (value == null || value.isNull) return emptyList()
            return listOf(value.stringValue ?: value.numberValue?.let(::numberText).orEmpty())
        }
    }
}

private class Zoomed(
    val label: String,
    val source: String,
)

internal enum class ChoiceArrangement { PICTURES, INLINE, LIST }

internal fun choiceArrangement(
    field: FormField,
    options: List<FieldOption>,
): ChoiceArrangement =
    when {
        options.any { !it.image.isNullOrEmpty() } -> ChoiceArrangement.PICTURES
        field.layout?.stringValue == "inline" -> ChoiceArrangement.INLINE
        else -> ChoiceArrangement.LIST
    }

internal val FormField.indicatorShape: IndicatorShape
    get() = if (type == "multiselect") IndicatorShape.CHECKBOX else IndicatorShape.RADIO

@Composable
private fun ChoiceList(
    context: FieldContext,
    shape: IndicatorShape,
) {
    val requester = remember { FocusRequester() }
    val options = context.options
    val arrangement = remember(options) { choiceArrangement(context.field, options) }
    val spec = LocalFormhausTokens.current.pictureGrid(context.field.columns)
    PrefetchImages(options)
    ChoiceScope(context) { actions ->
        FieldShell(context, variant = FieldShellVariant.GROUP) {
            val modifier = Modifier.formhausOptionsFocus(context, requester).selectableGroup()
            ChoiceOptions(context, shape, arrangement, options.indices, spec::columnCount, actions, modifier, requester)
        }
    }
}

@Composable
internal fun PrefetchImages(options: List<FieldOption>) {
    LaunchedEffect(options) { OptionImages.prefetch(options.take(PREFETCH_LIMIT).mapNotNull { it.safeImage }) }
}

@Composable
internal fun ChoiceScope(
    context: FieldContext,
    content: @Composable (ChoiceActions) -> Unit,
) {
    var zoomed by remember { mutableStateOf<Zoomed?>(null) }
    val actions = remember { ChoiceActions() }
    actions.context = context
    actions.zoom = { option, source -> zoomed = Zoomed(option.label ?: option.value.orEmpty(), source) }
    content(actions)
    zoomed?.let { shown -> ImageViewer(shown.label, shown.source, context.text("close")) { zoomed = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChoiceOptions(
    context: FieldContext,
    shape: IndicatorShape,
    arrangement: ChoiceArrangement,
    indices: IntRange,
    columnCount: (width: Dp, count: Int) -> Int,
    actions: ChoiceActions,
    modifier: Modifier,
    focus: FocusRequester? = null,
) {
    val tokens = LocalFormhausTokens.current
    val options = context.options
    val target = focus?.let { focusedOption(context) }
    val selected = ChoiceSelection.values(context.value, shape == IndicatorShape.CHECKBOX).toSet()
    val enabled = !context.isLocked
    val invalid = context.isInvalid
    val picture = arrangement == ChoiceArrangement.PICTURES
    val card: @Composable (Int) -> Unit = { index ->
        val option = options[index]
        val card = if (focus != null && index == target) Modifier.focusRequester(focus) else Modifier
        ChoiceCard(option, shape, option.value.orEmpty() in selected, picture, enabled, invalid, actions, card)
    }
    when (arrangement) {
        ChoiceArrangement.PICTURES -> {
            ColumnGrid(tokens.spacing.s5, columnCount, modifier) { indices.forEach { card(it) } }
        }

        ChoiceArrangement.INLINE -> {
            FlowRow(
                modifier,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s3),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.s3),
            ) { indices.forEach { index -> Column(Modifier.width(IntrinsicSize.Max)) { card(index) } } }
        }

        ChoiceArrangement.LIST -> {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(tokens.spacing.s3)) { indices.forEach { card(it) } }
        }
    }
}

internal fun FormhausTokens.pictureGrid(columns: Int?): GridSpec {
    val fixed =
        when {
            scale >= LARGE_TYPE -> 1
            columns != null && columns in 1..4 -> columns
            else -> null
        }
    val minimum: Dp = minOf(controls.optionImageWidth, (PICTURE_MINIMUM * maxOf(1f, scale)).dp)
    return GridSpec(fixed, if (fixed == null) minimum else 0.dp, 0.dp, spacing.s5)
}
