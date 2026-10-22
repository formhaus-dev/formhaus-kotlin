package dev.formhaus.compose.components

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.theme.LocalFormhausTokens

@Composable
public fun Modifier.inputChrome(
    focused: Boolean,
    invalid: Boolean,
    disabled: Boolean,
    padded: Boolean = true,
): Modifier {
    val tokens = LocalFormhausTokens.current
    val colors = tokens.colors
    val source = remember { MutableInteractionSource() }
    val border =
        when {
            focused -> colors.focus
            invalid -> colors.error
            else -> colors.inputBorder
        }
    val style =
        SurfaceStyle(
            tokens.controlShape,
            if (disabled) colors.backgroundDisabled else colors.background,
            border,
            hoverBorder = if (focused || invalid || disabled) border else colors.inputBorderHover,
            ring = colors.focus,
            ringRadius = tokens.radii.control,
            focused = focused,
        )
    return this
        .fillMaxWidth()
        .heightIn(min = tokens.controls.height)
        .animatedSurface(style, tokens.quickMotion, source)
        .hoverable(source)
        .padding(horizontal = if (padded) tokens.spacing.s6 else 0.dp, vertical = if (padded) tokens.spacing.s4 else 0.dp)
}

@Composable
public fun Modifier.focusRing(
    visible: Boolean,
    radius: Dp,
    offset: Dp = 1.dp,
): Modifier {
    val tokens = LocalFormhausTokens.current
    val style =
        SurfaceStyle(
            RectangleShape,
            Color.Transparent,
            Color.Transparent,
            ring = tokens.colors.focus,
            ringRadius = radius,
            ringOffset = offset,
            focused = visible,
        )
    return animatedSurface(style, tokens.quickMotion)
}
