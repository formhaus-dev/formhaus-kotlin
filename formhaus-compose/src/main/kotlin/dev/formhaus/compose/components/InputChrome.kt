package dev.formhaus.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val hovered by source.collectIsHoveredAsState()
    val border =
        when {
            focused -> colors.focus
            invalid -> colors.error
            hovered && !disabled -> colors.inputBorderHover
            else -> colors.inputBorder
        }
    val fill = if (disabled) colors.backgroundDisabled else colors.background
    return this
        .fillMaxWidth()
        .heightIn(min = tokens.controls.height)
        .focusRing(focused, tokens.radii.control)
        .surface(tokens.controlShape, fill, border)
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
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tokens.quick(), label = "ring")
    val color = tokens.colors.focus
    return drawWithContent {
        drawContent()
        if (alpha == 0f) return@drawWithContent
        val inset = (offset + 1.dp).toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(-inset, -inset),
            size = Size(size.width + 2 * inset, size.height + 2 * inset),
            cornerRadius = CornerRadius((radius + offset).toPx()),
            style = Stroke(2.dp.toPx()),
            alpha = alpha,
        )
    }
}
