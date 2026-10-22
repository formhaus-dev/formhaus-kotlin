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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.ColumnGrid
import dev.formhaus.compose.components.FieldShell
import dev.formhaus.compose.components.FieldShellVariant
import dev.formhaus.compose.components.GridSpec
import dev.formhaus.compose.components.ImageViewer
import dev.formhaus.compose.components.IndicatorShape
import dev.formhaus.compose.components.OptionImages
import dev.formhaus.compose.components.formhausFocus
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.form.numberText
import dev.formhaus.compose.theme.FormhausTokens
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FieldOption
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

@Composable
private fun ChoiceList(
    context: FieldContext,
    shape: IndicatorShape,
) {
    val requester = remember { FocusRequester() }
    var zoomed by remember { mutableStateOf<Zoomed?>(null) }
    val options = context.options
    LaunchedEffect(options) { OptionImages.prefetch(options.take(PREFETCH_LIMIT).mapNotNull { it.safeImage }) }
    FieldShell(context, variant = FieldShellVariant.GROUP) {
        ChoiceOptions(context, shape, Modifier.formhausFocus(context, requester).selectableGroup()) { option ->
            zoomed = Zoomed(option.label ?: option.value.orEmpty(), option.safeImage.orEmpty())
        }
    }
    zoomed?.let { shown -> ImageViewer(shown.label, shown.source, context.text("close")) { zoomed = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceOptions(
    context: FieldContext,
    shape: IndicatorShape,
    modifier: Modifier,
    zoom: (FieldOption) -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    val options = context.options
    val selected = ChoiceSelection.values(context.value, shape == IndicatorShape.CHECKBOX).toSet()
    val selection = ChoiceSelection(context)
    val card: @Composable (FieldOption, Boolean) -> Unit = { option, picture ->
        val value = option.value.orEmpty()
        ChoiceCard(
            option,
            shape,
            value in selected,
            picture,
            !context.isLocked,
            context.text("enlargeImage", stringMapOf("label" to JsonValue.String(option.label.orEmpty()))),
            context.optionTestId(value),
            { selection.choose(value) },
            { zoom(option) },
        )
    }
    when {
        options.any { !it.image.isNullOrEmpty() } -> {
            val spec = tokens.pictureGrid(context.field.columns)
            ColumnGrid(tokens.spacing.s5, spec::columnCount, modifier) { options.forEach { card(it, true) } }
        }

        context.field.layout?.stringValue == "inline" -> {
            FlowRow(
                modifier,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s3),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.s3),
            ) { options.forEach { option -> Column(Modifier.width(IntrinsicSize.Max)) { card(option, false) } } }
        }

        else -> {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(tokens.spacing.s3)) { options.forEach { card(it, false) } }
        }
    }
}

private fun FormhausTokens.pictureGrid(columns: Int?): GridSpec {
    val fixed =
        when {
            scale >= LARGE_TYPE -> 1
            columns != null && columns in 1..4 -> columns
            else -> null
        }
    val minimum: Dp = minOf(controls.optionImageWidth, (PICTURE_MINIMUM * maxOf(1f, scale)).dp)
    return GridSpec(fixed, if (fixed == null) minimum else 0.dp, 0.dp, spacing.s5)
}
