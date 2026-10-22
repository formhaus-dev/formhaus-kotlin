package dev.formhaus.compose.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

public data class Typography(
    val body: TextUnit,
    val small: TextUnit,
    val heading: TextUnit = (body.value + HEADING_STEP).sp,
    val title: TextUnit = (body.value + TITLE_STEP).sp,
    val fontFamily: FontFamily = FontFamily.Default,
) {
    public companion object {
        public val fine: Typography get() = fineTypography
        public val coarse: Typography get() = coarseTypography
    }
}

public data class Spacing(
    val s1: Dp,
    val s2: Dp,
    val s3: Dp,
    val s4: Dp,
    val s5: Dp,
    val s6: Dp,
    val s7: Dp,
    val s8: Dp,
    val s9: Dp,
    val s10: Dp,
    val gap: Dp,
    val cardPadding: Dp,
) {
    public companion object {
        public val regular: Spacing get() = regularSpacing
        public val compact: Spacing get() = compactSpacing
    }
}

public data class Radii(
    val control: Dp,
    val indicator: Dp = NativeMetrics.indicatorRadius,
) {
    public companion object {
        public val standard: Radii get() = standardRadii
    }
}

public data class Controls(
    val height: Dp,
    val iconButton: Dp,
    val star: Dp,
    val optionImageWidth: Dp,
    val hitTarget: Dp = NativeMetrics.hitTarget,
    val indicator: Dp = NativeMetrics.indicator,
    val textareaHeight: Dp = NativeMetrics.textareaHeight,
    val progressHeight: Dp = NativeMetrics.progressHeight,
    val maxWidth: Dp = NativeMetrics.maxWidth,
    val compactWidth: Dp = NativeMetrics.compactWidth,
    val matrixWidth: Dp = NativeMetrics.matrixWidth,
) {
    public companion object {
        public val fine: Controls get() = fineControls
        public val coarse: Controls get() = coarseControls
    }
}

public data class FormhausTheme(
    val light: Palette = Palette.light,
    val dark: Palette = Palette.dark,
    val typography: Typography,
    val spacing: Spacing = Spacing.regular,
    val radii: Radii = Radii.standard,
    val controls: Controls,
    val motion: Motion = Motion(),
    val lightIncreased: Palette? = null,
    val darkIncreased: Palette? = null,
) {
    public companion object {
        public val pointer: FormhausTheme =
            FormhausTheme(typography = Typography.fine, spacing = Spacing.regular, controls = Controls.fine)
        public val touch: FormhausTheme =
            FormhausTheme(
                typography = Typography.coarse,
                spacing = Spacing.compact,
                controls = Controls.coarse.copy(height = NativeMetrics.hitTarget),
            )
        public val standard: FormhausTheme get() = touch
    }
}
