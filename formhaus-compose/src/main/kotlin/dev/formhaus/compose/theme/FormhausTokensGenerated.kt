package dev.formhaus.compose.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val lightPalette: Palette =
    Palette(
        text = Color(0xFF18181B),
        textMuted = Color(0xFF5F5F68),
        border = Color(0xFFD4D4D8),
        inputBorder = Color(0xFFB1B8C2),
        borderHover = Color(0xFFA1A1AA),
        control = Color(0xFF71717A),
        error = Color(0xFFC0352D),
        errorHover = Color(0xFF9E2A23),
        background = Color(0xFFFFFFFF),
        backgroundDisabled = Color(0xFFF4F4F5),
        backgroundHover = Color(0xFFF6F6F7),
        primary = Color(0xFF5502FB),
        onPrimary = Color(0xFFFFFFFF),
        secondaryBorder = Color(0xFFD4D4D8),
        scrim = Color(0xFF141416).copy(alpha = 0.32f),
        shadow = Color(0xFF141416).copy(alpha = 0.2f),
        focus = Color(0xFF5502FB),
        primaryHover = Color(0xFF4602CE),
        primarySoft = Color(0xFFF0E8FF),
        errorSoft = Color(0xFFFAEFEE),
        inputBorderHover = Color(0xFF8E959F),
    )

internal val lightIncreasedPalette: Palette =
    lightPalette.copy(
        border = Color(0xFF84848D),
        inputBorder = Color(0xFF84848D),
        borderHover = Color(0xFF5F5F68),
        secondaryBorder = Color(0xFF84848D),
        inputBorderHover = Color(0xFF5F5F68),
    )

internal val darkPalette: Palette =
    Palette(
        text = Color(0xFFECECEE),
        textMuted = Color(0xFFA8A8B3),
        border = Color(0xFF3A3A41),
        inputBorder = Color(0xFF444B53),
        borderHover = Color(0xFF5A5A63),
        control = Color(0xFF80808B),
        error = Color(0xFFF2736A),
        errorHover = Color(0xFFF6958E),
        background = Color(0xFF18181C),
        backgroundDisabled = Color(0xFF232328),
        backgroundHover = Color(0xFF212126),
        primary = Color(0xFF7C4DFF),
        onPrimary = Color(0xFFFFFFFF),
        secondaryBorder = Color(0xFF3A3A41),
        scrim = Color(0xFF000000).copy(alpha = 0.55f),
        shadow = Color(0xFF000000).copy(alpha = 0.6f),
        focus = Color(0xFF7C4DFF),
        primaryHover = Color(0xFF9068FF),
        primarySoft = Color(0xFF211D30),
        errorSoft = Color(0xFF291F22),
        inputBorderHover = Color(0xFF5E656D),
    )

internal val darkIncreasedPalette: Palette =
    darkPalette.copy(
        border = Color(0xFF73737D),
        inputBorder = Color(0xFF73737D),
        borderHover = Color(0xFF9A9AA5),
        secondaryBorder = Color(0xFF73737D),
        inputBorderHover = Color(0xFF9A9AA5),
    )

internal val lightColorMixes: ColorMixes =
    ColorMixes(
        primaryHover = 0.82f,
        primaryHoverWith = Color(0xFF000000),
        primarySoft = 0.09f,
        errorSoft = 0.08f,
    )

internal val darkColorMixes: ColorMixes =
    ColorMixes(
        primaryHover = 0.85f,
        primaryHoverWith = Color(0xFFFFFFFF),
        primarySoft = 0.09f,
        errorSoft = 0.08f,
    )

internal const val HEADING_STEP: Float = 2f

internal const val TITLE_STEP: Float = 5f

internal val fineTypography: Typography =
    Typography(
        body = 15.sp,
        small = 13.sp,
        heading = 17.sp,
        title = 20.sp,
    )

internal val coarseTypography: Typography =
    fineTypography.copy(
        body = 17.sp,
        small = 15.sp,
        heading = 19.sp,
        title = 22.sp,
    )

internal val regularSpacing: Spacing =
    Spacing(
        s1 = 4.dp,
        s2 = 6.dp,
        s3 = 8.dp,
        s4 = 10.dp,
        s5 = 12.dp,
        s6 = 14.dp,
        s7 = 16.dp,
        s8 = 18.dp,
        s9 = 20.dp,
        s10 = 24.dp,
        gap = 28.dp,
        cardPadding = 24.dp,
    )

internal val compactSpacing: Spacing =
    regularSpacing.copy(
        cardPadding = 16.dp,
    )

internal val standardRadii: Radii =
    Radii(
        control = 10.dp,
    )

internal val fineControls: Controls =
    Controls(
        height = 46.dp,
        iconButton = 32.dp,
        star = 32.dp,
        optionImageWidth = 200.dp,
    )

internal val coarseControls: Controls =
    fineControls.copy(
        star = 40.dp,
    )

internal object RaisedShadow {
    val x: Dp = 0.dp
    val y: Dp = 2.dp
    val blur: Dp = 8.dp
}
