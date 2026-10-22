package dev.formhaus.compose.theme

import androidx.compose.ui.graphics.Color

public data class Palette(
    val text: Color = lightPalette.text,
    val textMuted: Color = lightPalette.textMuted,
    val border: Color = lightPalette.border,
    val inputBorder: Color = lightPalette.inputBorder,
    val borderHover: Color = lightPalette.borderHover,
    val control: Color = lightPalette.control,
    val error: Color = lightPalette.error,
    val errorHover: Color = lightPalette.errorHover,
    val background: Color = lightPalette.background,
    val backgroundDisabled: Color = lightPalette.backgroundDisabled,
    val backgroundHover: Color = lightPalette.backgroundHover,
    val primary: Color = lightPalette.primary,
    val onPrimary: Color = lightPalette.onPrimary,
    val secondaryBorder: Color = lightPalette.secondaryBorder,
    val scrim: Color = lightPalette.scrim,
    val shadow: Color = lightPalette.shadow,
    val focus: Color? = null,
    val primaryHover: Color? = null,
    val primarySoft: Color? = null,
    val errorSoft: Color? = null,
    val inputBorderHover: Color? = null,
) {
    public companion object {
        public val light: Palette get() = lightPalette
        public val dark: Palette get() = darkPalette
        internal val lightIncreased: Palette get() = lightIncreasedPalette
        internal val darkIncreased: Palette get() = darkIncreasedPalette
    }
}

internal data class ColorMixes(
    val primaryHover: Float,
    val primaryHoverWith: Color,
    val primarySoft: Float,
    val errorSoft: Float,
)

public class ResolvedPalette internal constructor(
    palette: Palette,
    dark: Boolean,
) {
    private val mixes = if (dark) darkColorMixes else lightColorMixes
    public val text: Color = palette.text
    public val textMuted: Color = palette.textMuted
    public val border: Color = palette.border
    public val inputBorder: Color = palette.inputBorder
    public val inputBorderHover: Color = palette.inputBorderHover ?: mix(palette.text, palette.inputBorder, 0.4f)
    public val borderHover: Color = palette.borderHover
    public val control: Color = palette.control
    public val error: Color = palette.error
    public val errorHover: Color = palette.errorHover
    public val errorSoft: Color = palette.errorSoft ?: mix(palette.error, palette.background, mixes.errorSoft)
    public val background: Color = palette.background
    public val backgroundDisabled: Color = palette.backgroundDisabled
    public val backgroundHover: Color = palette.backgroundHover
    public val primary: Color = palette.primary
    public val onPrimary: Color = palette.onPrimary
    public val primaryHover: Color =
        palette.primaryHover ?: mix(palette.primary, mixes.primaryHoverWith, mixes.primaryHover)
    public val primarySoft: Color = palette.primarySoft ?: mix(palette.primary, palette.background, mixes.primarySoft)
    public val primaryOnSoft: Color = if (dark) mix(palette.primary, Color.White, 0.6f) else palette.primary
    public val primaryDisabled: Color = mix(palette.primary, palette.background, if (dark) 0.3f else 0.1f)
    public val secondaryBorder: Color = palette.secondaryBorder
    public val focus: Color = palette.focus ?: palette.primary
    public val scrim: Color = palette.scrim
    public val shadow: Color = palette.shadow
}

internal fun mix(
    color: Color,
    other: Color,
    amount: Float,
): Color {
    val rest = 1 - amount
    return Color(
        red = color.red * amount + other.red * rest,
        green = color.green * amount + other.green * rest,
        blue = color.blue * amount + other.blue * rest,
    )
}
