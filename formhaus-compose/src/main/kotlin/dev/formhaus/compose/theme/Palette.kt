package dev.formhaus.compose.theme

import androidx.compose.ui.graphics.Color
import dev.formhaus.core.json.ValueParts
import dev.formhaus.core.json.describe
import dev.formhaus.core.json.equalParts
import dev.formhaus.core.json.partsHash

@Suppress("LongParameterList")
public class Palette(
    public val text: Color = lightPalette.text,
    public val textMuted: Color = lightPalette.textMuted,
    public val border: Color = lightPalette.border,
    public val inputBorder: Color = lightPalette.inputBorder,
    public val borderHover: Color = lightPalette.borderHover,
    public val control: Color = lightPalette.control,
    public val error: Color = lightPalette.error,
    public val errorHover: Color = lightPalette.errorHover,
    public val background: Color = lightPalette.background,
    public val backgroundDisabled: Color = lightPalette.backgroundDisabled,
    public val backgroundHover: Color = lightPalette.backgroundHover,
    public val primary: Color = lightPalette.primary,
    public val onPrimary: Color = lightPalette.onPrimary,
    public val secondaryBorder: Color = lightPalette.secondaryBorder,
    public val scrim: Color = lightPalette.scrim,
    public val shadow: Color = lightPalette.shadow,
    public val focus: Color? = null,
    public val primaryHover: Color? = null,
    public val primarySoft: Color? = null,
    public val errorSoft: Color? = null,
    public val inputBorderHover: Color? = null,
) {
    public fun copy(
        text: Color = this.text,
        textMuted: Color = this.textMuted,
        border: Color = this.border,
        inputBorder: Color = this.inputBorder,
        borderHover: Color = this.borderHover,
        control: Color = this.control,
        error: Color = this.error,
        errorHover: Color = this.errorHover,
        background: Color = this.background,
        backgroundDisabled: Color = this.backgroundDisabled,
        backgroundHover: Color = this.backgroundHover,
        primary: Color = this.primary,
        onPrimary: Color = this.onPrimary,
        secondaryBorder: Color = this.secondaryBorder,
        scrim: Color = this.scrim,
        shadow: Color = this.shadow,
        focus: Color? = this.focus,
        primaryHover: Color? = this.primaryHover,
        primarySoft: Color? = this.primarySoft,
        errorSoft: Color? = this.errorSoft,
        inputBorderHover: Color? = this.inputBorderHover,
    ): Palette =
        Palette(
            text,
            textMuted,
            border,
            inputBorder,
            borderHover,
            control,
            error,
            errorHover,
            background,
            backgroundDisabled,
            backgroundHover,
            primary,
            onPrimary,
            secondaryBorder,
            scrim,
            shadow,
            focus,
            primaryHover,
            primarySoft,
            errorSoft,
            inputBorderHover,
        )

    override fun equals(other: Any?): Boolean = equalParts(other, Palette::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("Palette")

    private fun parts(): ValueParts =
        listOf(
            "text" to text,
            "textMuted" to textMuted,
            "border" to border,
            "inputBorder" to inputBorder,
            "borderHover" to borderHover,
            "control" to control,
            "error" to error,
            "errorHover" to errorHover,
            "background" to background,
            "backgroundDisabled" to backgroundDisabled,
            "backgroundHover" to backgroundHover,
            "primary" to primary,
            "onPrimary" to onPrimary,
            "secondaryBorder" to secondaryBorder,
            "scrim" to scrim,
            "shadow" to shadow,
            "focus" to focus,
            "primaryHover" to primaryHover,
            "primarySoft" to primarySoft,
            "errorSoft" to errorSoft,
            "inputBorderHover" to inputBorderHover,
        )

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
