package dev.formhaus.compose.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.formhaus.compose.model.ValueParts
import dev.formhaus.compose.model.describe
import dev.formhaus.compose.model.equalParts
import dev.formhaus.compose.model.partsHash

public class Typography(
    public val body: TextUnit,
    public val small: TextUnit,
    public val heading: TextUnit = (body.value + HEADING_STEP).sp,
    public val title: TextUnit = (body.value + TITLE_STEP).sp,
    public val fontFamily: FontFamily = FontFamily.Default,
) {
    public fun copy(
        body: TextUnit = this.body,
        small: TextUnit = this.small,
        heading: TextUnit = this.heading,
        title: TextUnit = this.title,
        fontFamily: FontFamily = this.fontFamily,
    ): Typography = Typography(body, small, heading, title, fontFamily)

    override fun equals(other: Any?): Boolean = equalParts(other, Typography::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("Typography")

    private fun parts(): ValueParts =
        listOf(
            "body" to body,
            "small" to small,
            "heading" to heading,
            "title" to title,
            "fontFamily" to fontFamily,
        )

    public companion object {
        public val fine: Typography get() = fineTypography
        public val coarse: Typography get() = coarseTypography
    }
}

@Suppress("LongParameterList")
public class Spacing(
    public val s1: Dp,
    public val s2: Dp,
    public val s3: Dp,
    public val s4: Dp,
    public val s5: Dp,
    public val s6: Dp,
    public val s7: Dp,
    public val s8: Dp,
    public val s9: Dp,
    public val s10: Dp,
    public val gap: Dp,
    public val cardPadding: Dp,
) {
    public fun copy(
        s1: Dp = this.s1,
        s2: Dp = this.s2,
        s3: Dp = this.s3,
        s4: Dp = this.s4,
        s5: Dp = this.s5,
        s6: Dp = this.s6,
        s7: Dp = this.s7,
        s8: Dp = this.s8,
        s9: Dp = this.s9,
        s10: Dp = this.s10,
        gap: Dp = this.gap,
        cardPadding: Dp = this.cardPadding,
    ): Spacing = Spacing(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, gap, cardPadding)

    override fun equals(other: Any?): Boolean = equalParts(other, Spacing::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("Spacing")

    private fun parts(): ValueParts =
        listOf(
            "s1" to s1,
            "s2" to s2,
            "s3" to s3,
            "s4" to s4,
            "s5" to s5,
            "s6" to s6,
            "s7" to s7,
            "s8" to s8,
            "s9" to s9,
            "s10" to s10,
            "gap" to gap,
            "cardPadding" to cardPadding,
        )

    public companion object {
        public val regular: Spacing get() = regularSpacing
        public val compact: Spacing get() = compactSpacing
    }
}

public class Radii(
    public val control: Dp,
    public val indicator: Dp = NativeMetrics.indicatorRadius,
) {
    public fun copy(
        control: Dp = this.control,
        indicator: Dp = this.indicator,
    ): Radii = Radii(control, indicator)

    override fun equals(other: Any?): Boolean = equalParts(other, Radii::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("Radii")

    private fun parts(): ValueParts = listOf("control" to control, "indicator" to indicator)

    public companion object {
        public val standard: Radii get() = standardRadii
    }
}

@Suppress("LongParameterList")
public class Controls(
    public val height: Dp,
    public val iconButton: Dp,
    public val star: Dp,
    public val optionImageWidth: Dp,
    public val hitTarget: Dp = NativeMetrics.hitTarget,
    public val indicator: Dp = NativeMetrics.indicator,
    public val textareaHeight: Dp = NativeMetrics.textareaHeight,
    public val progressHeight: Dp = NativeMetrics.progressHeight,
    public val maxWidth: Dp = NativeMetrics.maxWidth,
    public val compactWidth: Dp = NativeMetrics.compactWidth,
    public val matrixWidth: Dp = NativeMetrics.matrixWidth,
) {
    public fun copy(
        height: Dp = this.height,
        iconButton: Dp = this.iconButton,
        star: Dp = this.star,
        optionImageWidth: Dp = this.optionImageWidth,
        hitTarget: Dp = this.hitTarget,
        indicator: Dp = this.indicator,
        textareaHeight: Dp = this.textareaHeight,
        progressHeight: Dp = this.progressHeight,
        maxWidth: Dp = this.maxWidth,
        compactWidth: Dp = this.compactWidth,
        matrixWidth: Dp = this.matrixWidth,
    ): Controls =
        Controls(
            height,
            iconButton,
            star,
            optionImageWidth,
            hitTarget,
            indicator,
            textareaHeight,
            progressHeight,
            maxWidth,
            compactWidth,
            matrixWidth,
        )

    override fun equals(other: Any?): Boolean = equalParts(other, Controls::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("Controls")

    private fun parts(): ValueParts =
        listOf(
            "height" to height,
            "iconButton" to iconButton,
            "star" to star,
            "optionImageWidth" to optionImageWidth,
            "hitTarget" to hitTarget,
            "indicator" to indicator,
            "textareaHeight" to textareaHeight,
            "progressHeight" to progressHeight,
            "maxWidth" to maxWidth,
            "compactWidth" to compactWidth,
            "matrixWidth" to matrixWidth,
        )

    public companion object {
        public val fine: Controls get() = fineControls
        public val coarse: Controls get() = coarseControls
    }
}

@Suppress("LongParameterList")
public class FormhausTheme(
    public val light: Palette = Palette.light,
    public val dark: Palette = Palette.dark,
    public val typography: Typography,
    public val spacing: Spacing = Spacing.regular,
    public val radii: Radii = Radii.standard,
    public val controls: Controls,
    public val motion: Motion = Motion(),
    public val lightIncreased: Palette? = null,
    public val darkIncreased: Palette? = null,
) {
    public fun copy(
        light: Palette = this.light,
        dark: Palette = this.dark,
        typography: Typography = this.typography,
        spacing: Spacing = this.spacing,
        radii: Radii = this.radii,
        controls: Controls = this.controls,
        motion: Motion = this.motion,
        lightIncreased: Palette? = this.lightIncreased,
        darkIncreased: Palette? = this.darkIncreased,
    ): FormhausTheme = FormhausTheme(light, dark, typography, spacing, radii, controls, motion, lightIncreased, darkIncreased)

    override fun equals(other: Any?): Boolean = equalParts(other, FormhausTheme::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("FormhausTheme")

    private fun parts(): ValueParts =
        listOf(
            "light" to light,
            "dark" to dark,
            "typography" to typography,
            "spacing" to spacing,
            "radii" to radii,
            "controls" to controls,
            "motion" to motion,
            "lightIncreased" to lightIncreased,
            "darkIncreased" to darkIncreased,
        )

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
