package dev.formhaus.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.theme.LocalFormhausTokens

@Composable
public fun ChoiceIndicator(
    shape: IndicatorShape,
    selected: Boolean,
    modifier: Modifier = Modifier,
    invalid: Boolean = false,
) {
    val tokens = LocalFormhausTokens.current
    val colors = tokens.colors
    val size = tokens.controls.indicator
    val stroke =
        when {
            selected -> colors.primary
            invalid -> colors.error
            else -> colors.control
        }
    Box(modifier.size(size).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if (shape == IndicatorShape.RADIO) {
            Box(Modifier.size(size).surface(CircleShape, colors.background, stroke, 2.dp))
            Box(Modifier.size(size * 4 / 9).fade(selected, tokens.quickMotion).background(colors.primary, CircleShape))
        } else {
            val fill = if (selected) colors.primary else colors.background
            Box(Modifier.size(size).surface(RoundedCornerShape(tokens.radii.indicator), fill, stroke, 1.5.dp))
            Icon(FormhausIcons.check, null, Modifier.size(size * 0.72f).fade(selected, tokens.quickMotion), tint = colors.onPrimary)
        }
    }
}

public enum class IndicatorShape { RADIO, CHECKBOX }
