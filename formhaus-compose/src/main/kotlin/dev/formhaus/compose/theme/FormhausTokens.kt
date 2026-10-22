package dev.formhaus.compose.theme

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

public class FormhausTokens internal constructor(
    theme: FormhausTheme,
    dark: Boolean,
    fontScale: Float,
    public val reduceMotion: Boolean,
    increasedContrast: Boolean = false,
) {
    public val colors: ResolvedPalette = ResolvedPalette(theme.palette(dark, increasedContrast), dark)
    public val typography: Typography = theme.typography
    public val spacing: Spacing
    public val radii: Radii = theme.radii
    public val controls: Controls
    public val motion: Motion = theme.motion
    public val scale: Float = fontScale

    init {
        val grow = maxOf(1f, fontScale)
        spacing = theme.spacing.scaled(1 + (grow - 1) / 2)
        controls = theme.controls.scaled(grow)
    }

    public fun <T> animation(durationMillis: Int): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else tween(durationMillis, easing = EaseOut)

    public fun <T> quick(): FiniteAnimationSpec<T> = animation(motion.quick)
}

private fun Spacing.scaled(factor: Float): Spacing =
    Spacing(
        s1 = s1 * factor,
        s2 = s2 * factor,
        s3 = s3 * factor,
        s4 = s4 * factor,
        s5 = s5 * factor,
        s6 = s6 * factor,
        s7 = s7 * factor,
        s8 = s8 * factor,
        s9 = s9 * factor,
        s10 = s10 * factor,
        gap = gap * factor,
        cardPadding = cardPadding * factor,
    )

private fun Controls.scaled(factor: Float): Controls =
    copy(
        height = height * factor,
        star = star * factor,
        indicator = indicator * factor,
        textareaHeight = textareaHeight * factor,
        matrixWidth = matrixWidth * factor,
        optionImageWidth = optionImageWidth * factor,
    )
