package dev.formhaus.compose.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import kotlin.math.roundToInt

private const val PRESSED_SCALE = 0.99f
private const val DISABLED_ALPHA = 0.6f

@Composable
public fun OptionCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    invalid: Boolean = false,
    enabled: Boolean = true,
    shape: IndicatorShape = IndicatorShape.RADIO,
    content: @Composable () -> Unit,
) {
    OptionCardBox(selected, onClick, modifier, invalid, enabled, shape, LocalFormhausTokens.current.spacing.s5, content)
}

@Composable
internal fun OptionCardBox(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    invalid: Boolean,
    enabled: Boolean,
    shape: IndicatorShape,
    top: Dp,
    content: @Composable () -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = tokens.controls.hitTarget)
            .selectableSurface(selected, invalid, solid = false, enabled = enabled, source = source, card = true)
            .focusable(enabled, source)
            .choice(selected, shape, enabled, source, onClick)
            .padding(start = tokens.spacing.s6, end = tokens.spacing.s6, top = top, bottom = tokens.spacing.s5),
    ) {
        content()
    }
}

internal fun Modifier.choice(
    selected: Boolean,
    shape: IndicatorShape,
    enabled: Boolean,
    source: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier =
    if (shape == IndicatorShape.CHECKBOX) {
        toggleable(selected, source, null, enabled, Role.Checkbox) { onClick() }
    } else {
        selectable(selected, source, null, enabled, Role.RadioButton, onClick)
    }

@Composable
internal fun Modifier.selectableSurface(
    selected: Boolean,
    invalid: Boolean,
    solid: Boolean,
    enabled: Boolean,
    source: MutableInteractionSource,
    card: Boolean = false,
): Modifier {
    val tokens = LocalFormhausTokens.current
    val colors = tokens.colors
    val fill = if (selected) (if (solid) colors.primary else colors.primarySoft) else colors.background
    val border =
        when {
            selected -> colors.primary
            invalid -> colors.error
            else -> colors.inputBorder
        }
    val quiet = selected || !enabled
    val style =
        SurfaceStyle(
            tokens.controlShape,
            fill,
            border,
            hoverFill = if (quiet) fill else colors.backgroundHover,
            hoverBorder = if (quiet || invalid) border else colors.inputBorderHover,
            ring = if (card) colors.focus else Color.Unspecified,
            ringRadius = tokens.radii.control,
            pressScale = if (card) PRESSED_SCALE else 1f,
        )
    return (if (enabled) this else alpha(DISABLED_ALPHA)).animatedSurface(style, tokens.quickMotion, source)
}

@Composable
public fun OptionRow(
    shape: IndicatorShape,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    invalid: Boolean = false,
) {
    val tokens = LocalFormhausTokens.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s5)) {
        ChoiceIndicator(shape, selected, alignedToText(), invalid)
        Text(label, Modifier.weight(1f).alignBy(FirstBaseline), style = tokens.textStyle(TextRole.option))
    }
}

@Composable
internal fun RowScope.alignedToText(): Modifier {
    val tokens = LocalFormhausTokens.current
    val offset = with(LocalDensity.current) { (tokens.typography.body.toPx() * 0.36f).roundToInt() }
    return Modifier.alignBy { it.measuredHeight / 2 + offset }
}
