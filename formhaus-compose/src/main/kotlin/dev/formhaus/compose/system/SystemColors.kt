package dev.formhaus.compose.system

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import dev.formhaus.compose.theme.FormhausTheme
import dev.formhaus.compose.theme.Palette

private const val DISABLED_TINT = 0.06f
private const val HOVER_TINT = 0.08f

internal fun FormhausTheme.materialColors(scheme: ColorScheme): FormhausTheme {
    val palette = scheme.formhausPalette()
    return copy(light = palette, dark = palette, lightIncreased = null, darkIncreased = null)
}

private fun ColorScheme.formhausPalette(): Palette =
    Palette(
        text = onSurface,
        textMuted = onSurfaceVariant,
        border = outlineVariant,
        inputBorder = outline,
        borderHover = outline,
        control = outline,
        error = error,
        errorHover = error,
        background = surface,
        backgroundDisabled = onSurface.copy(alpha = DISABLED_TINT).compositeOver(surface),
        backgroundHover = onSurface.copy(alpha = HOVER_TINT).compositeOver(surface),
        primary = primary,
        onPrimary = onPrimary,
        secondaryBorder = outline,
        scrim = scrim,
        shadow = Color.Black,
        focus = primary,
        primarySoft = primaryContainer,
        errorSoft = errorContainer,
    )
