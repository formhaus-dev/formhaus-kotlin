package dev.formhaus.compose.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.theme.FormhausTokens
import dev.formhaus.compose.theme.LocalFormhausTokens

@Composable
internal fun Modifier.surface(
    shape: Shape,
    fill: Color,
    border: Color,
    lineWidth: Dp = 1.dp,
): Modifier {
    val tokens = LocalFormhausTokens.current
    return animatedSurface(SurfaceStyle(shape, fill, border, lineWidth), tokens.quickMotion)
}

internal val FormhausTokens.controlShape: Shape get() = RoundedCornerShape(radii.control)

internal val FormhausTokens.innerShape: Shape get() = RoundedCornerShape(maxOf(0.dp, radii.control - spacing.s1))
