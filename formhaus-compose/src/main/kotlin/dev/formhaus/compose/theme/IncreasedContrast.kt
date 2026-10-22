package dev.formhaus.compose.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

public fun FormhausTheme.palette(
    dark: Boolean,
    increasedContrast: Boolean,
): Palette {
    val base = if (dark) this.dark else light
    if (!increasedContrast) return base
    (if (dark) darkIncreased else lightIncreased)?.let { return it }
    if (base != (if (dark) Palette.dark else Palette.light)) return base.raisingContrast()
    return (if (dark) Palette.darkIncreased else Palette.lightIncreased).raisingText()
}

internal fun Palette.raisingContrast(): Palette {
    val raised = raisingText()
    val inputBorder = raise(inputBorder, 4.5)
    val inputBorderHover = raise(inputBorderHover ?: text, 7.0)
    return raised.copy(
        inputBorder = inputBorder,
        inputBorderHover = inputBorderHover,
        border = inputBorder,
        borderHover = inputBorderHover,
        secondaryBorder = inputBorder,
    )
}

internal fun Palette.raisingText(): Palette {
    val primary = raise(primary, 4.5)
    val onPrimary =
        when {
            contrastRatio(onPrimary, primary) >= 4.5 -> onPrimary
            contrastRatio(background, primary) >= contrastRatio(text, primary) -> background
            else -> text
        }
    return copy(
        textMuted = raise(textMuted, 7.0),
        control = raise(control, 4.5),
        error = raise(error, 4.5),
        primary = primary,
        onPrimary = onPrimary,
    )
}

private const val RAISE_STEPS = 20

private fun Palette.raise(
    color: Color,
    target: Double,
): Color =
    (0..RAISE_STEPS)
        .asSequence()
        .map { step -> mix(text, color, step.toFloat() / RAISE_STEPS) }
        .firstOrNull { contrastRatio(it, background) >= target } ?: text

internal fun contrastRatio(
    lhs: Color,
    rhs: Color,
): Double {
    val values = listOf(lhs.luminance(), rhs.luminance()).sorted()
    return (values[1] + 0.05) / (values[0] + 0.05)
}
