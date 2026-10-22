package dev.formhaus.compose.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.formhaus.compose.components.IndicatorShape
import dev.formhaus.compose.components.OptionCardBox
import dev.formhaus.compose.components.OptionImage
import dev.formhaus.compose.components.OptionRow
import dev.formhaus.compose.components.ZoomButton
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.model.formhausTestId
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.renderer.safeImage

@Composable
internal fun ChoiceCard(
    option: FieldOption,
    shape: IndicatorShape,
    selected: Boolean,
    picture: Boolean,
    enabled: Boolean,
    invalid: Boolean,
    actions: ChoiceActions,
) {
    val tokens = LocalFormhausTokens.current
    val value = option.value.orEmpty()
    val image = remember(option) { option.safeImage }
    val shown = image.takeIf { picture }
    val fill = if (picture) Modifier.fillMaxHeight() else Modifier
    Box(fill) {
        OptionCardBox(
            selected,
            { actions.choose(value) },
            fill.formhausTestId(actions.testId(value)),
            invalid = invalid,
            enabled = enabled,
            shape = shape,
            top = if (picture) tokens.spacing.s3 else tokens.spacing.s5,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.s3)) {
                if (shown != null) OptionImage(shown)
                OptionRow(shape, option.label ?: value, selected, invalid = invalid)
            }
        }
        if (shown != null) {
            val inset = (tokens.controls.hitTarget - tokens.controls.iconButton) / 2
            ZoomButton(
                actions.zoomLabel(option),
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = inset - tokens.spacing.s6 - tokens.spacing.s3, y = tokens.spacing.s3 * 2 - inset),
                action = { actions.zoom(option, shown) },
            )
        }
    }
}

internal class ChoiceActions {
    var context: FieldContext? = null
    var zoom: (FieldOption, String) -> Unit = { _, _ -> }

    fun choose(value: String) {
        context?.let { ChoiceSelection(it).choose(value) }
    }

    fun zoomLabel(option: FieldOption): String =
        context?.text("enlargeImage", stringMapOf("label" to JsonValue.String(option.label.orEmpty()))).orEmpty()

    fun testId(value: String): String? = context?.optionTestId(value)
}
