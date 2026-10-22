package dev.formhaus.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import dev.formhaus.compose.system.materialColors

public val LocalFormhausTheme: ProvidableCompositionLocal<FormhausTheme> =
    staticCompositionLocalOf { FormhausTheme.standard }

public val LocalFormhausTokens: ProvidableCompositionLocal<FormhausTokens> =
    compositionLocalOf { FormhausTokens(FormhausTheme.standard, dark = false, fontScale = 1f, reduceMotion = false) }

@Composable
public fun FormhausTheme(
    theme: FormhausTheme,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFormhausTheme provides theme, content = content)
}

@Composable
internal fun ResolveFormhausTokens(
    system: Boolean,
    content: @Composable () -> Unit,
) {
    val base = LocalFormhausTheme.current
    val scheme = MaterialTheme.colorScheme
    val theme = if (system) remember(base, scheme) { base.materialColors(scheme) } else base
    val dark = isSystemInDarkTheme()
    val fontScale = LocalDensity.current.fontScale
    val reduceMotion = rememberReduceMotion()
    val increasedContrast = rememberIncreasedContrast()
    val tokens =
        remember(theme, dark, fontScale, reduceMotion, increasedContrast) {
            FormhausTokens(theme, dark, fontScale, reduceMotion, increasedContrast)
        }
    if (system) {
        CompositionLocalProvider(LocalFormhausTokens provides tokens, content = content)
    } else {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.tinted(tokens.colors)) {
            CompositionLocalProvider(
                LocalFormhausTokens provides tokens,
                LocalContentColor provides tokens.colors.text,
                content = content,
            )
        }
    }
}

internal fun ColorScheme.tinted(colors: ResolvedPalette): ColorScheme =
    copy(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.primarySoft,
        onPrimaryContainer = colors.primaryOnSoft,
        error = colors.error,
        errorContainer = colors.errorSoft,
        background = colors.background,
        onBackground = colors.text,
        surface = colors.background,
        onSurface = colors.text,
        onSurfaceVariant = colors.textMuted,
        outline = colors.inputBorder,
        outlineVariant = colors.border,
        scrim = colors.scrim,
        surfaceTint = colors.primary,
    )
