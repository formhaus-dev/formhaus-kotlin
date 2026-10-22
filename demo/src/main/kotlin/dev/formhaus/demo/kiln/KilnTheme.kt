package dev.formhaus.demo.kiln

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.formhaus.compose.form.FormComponents
import dev.formhaus.compose.theme.FormhausTheme
import dev.formhaus.compose.theme.Palette

val kilnComponents: FormComponents =
    FormComponents.standard
        .field("text") { KilnTextField(it) }
        .field("email") { KilnTextField(it) }
        .field("radio") { KilnChoiceField(it) }
        .field("multiselect") { KilnChoiceField(it) }
        .field("scale") { KilnScaleField(it) }
        .field("rating") { KilnRatingField(it) }
        .section { KilnSection(it) }
        .progress { KilnStepHeader(it) }
        .actions { KilnActions(it) }

val kilnTheme: FormhausTheme =
    FormhausTheme.standard.copy(
        light =
            Palette(
                text = Color(0xFF2B2118),
                textMuted = Color(0xFF76665A),
                border = Color(0xFFDDCFBF),
                inputBorder = Color(0xFF9A8878),
                borderHover = Color(0xFFB9A693),
                control = Color(0xFF7A6A5C),
                error = Color(0xFFB42318),
                errorHover = Color(0xFF912018),
                background = Color(0xFFFBF6EF),
                backgroundDisabled = Color(0xFFF1E8DC),
                backgroundHover = Color(0xFFF5EDE2),
                primary = Color(0xFFB8502A),
                onPrimary = Color(0xFFFFF8F1),
                secondaryBorder = Color(0xFF968270),
            ),
        dark =
            Palette.dark.copy(
                text = Color(0xFFF3EAE0),
                textMuted = Color(0xFFB8A898),
                border = Color(0xFF463B31),
                inputBorder = Color(0xFF746556),
                borderHover = Color(0xFF66574A),
                control = Color(0xFF9C8B7C),
                error = Color(0xFFFF8A80),
                errorHover = Color(0xFFFFADA6),
                background = Color(0xFF1D1712),
                backgroundDisabled = Color(0xFF2A221B),
                backgroundHover = Color(0xFF271F18),
                primary = Color(0xFFE8825A),
                onPrimary = Color(0xFF1D1712),
                secondaryBorder = Color(0xFF806C5C),
                focus = null,
                primaryHover = null,
                primarySoft = null,
                errorSoft = null,
                inputBorderHover = null,
            ),
        radii = FormhausTheme.standard.radii.copy(control = 22.dp),
    )

internal object KilnType {
    val title = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp)
    val heading = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp)
    val label = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
    val body = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)
    val note = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val eyebrow = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp)
}
