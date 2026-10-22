package dev.formhaus.compose.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

public data class TextRole(
    val size: Size,
    val weight: FontWeight = FontWeight.Normal,
    val leading: Float = 1.5f,
    val tone: Tone = Tone.TEXT,
    val tabularDigits: Boolean = false,
) {
    public enum class Size { BODY, SMALL, HEADING, TITLE }

    public enum class Tone { TEXT, MUTED, ERROR }

    internal fun size(typography: Typography): TextUnit =
        when (size) {
            Size.BODY -> typography.body
            Size.SMALL -> typography.small
            Size.HEADING -> typography.heading
            Size.TITLE -> typography.title
        }

    internal fun color(colors: ResolvedPalette): Color =
        when (tone) {
            Tone.TEXT -> colors.text
            Tone.MUTED -> colors.textMuted
            Tone.ERROR -> colors.error
        }

    public companion object {
        public val body: TextRole = TextRole(Size.BODY)
        public val label: TextRole = TextRole(Size.BODY, FontWeight.SemiBold, 1.4f)
        public val option: TextRole = TextRole(Size.BODY)
        public val helper: TextRole = TextRole(Size.SMALL, leading = 1.45f, tone = Tone.MUTED)
        public val error: TextRole = TextRole(Size.SMALL, FontWeight.Medium, 1.45f, Tone.ERROR)
        public val heading: TextRole = TextRole(Size.HEADING, FontWeight.SemiBold, 1.35f)
        public val title: TextRole = TextRole(Size.TITLE, FontWeight.SemiBold, 1.3f)
        public val counter: TextRole = TextRole(Size.SMALL, leading = 1.45f, tone = Tone.MUTED, tabularDigits = true)
        public val itemTitle: TextRole = TextRole(Size.SMALL, FontWeight.SemiBold, 1.4f, Tone.MUTED, tabularDigits = true)
        public val caption: TextRole = TextRole(Size.SMALL, FontWeight.Medium, 1.3f, Tone.MUTED)
        public val button: TextRole = TextRole(Size.BODY, FontWeight.SemiBold, 1.2f)
        public val textButton: TextRole = TextRole(Size.BODY, FontWeight.Medium, 1.2f, Tone.MUTED)
    }
}

public fun FormhausTokens.textStyle(
    role: TextRole,
    colored: Boolean = true,
): TextStyle =
    TextStyle(
        color = if (colored) role.color(colors) else Color.Unspecified,
        fontSize = role.size(typography),
        fontWeight = role.weight,
        fontFamily = typography.fontFamily,
        lineHeight = role.leading.em,
        fontFeatureSettings = if (role.tabularDigits) "tnum" else null,
    )
