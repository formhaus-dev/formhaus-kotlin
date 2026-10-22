package dev.formhaus.compose.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

private val paletteTokens: List<Pair<(Palette) -> Color?, String>> =
    listOf(
        Palette::text to "color.text.default",
        Palette::textMuted to "color.text.muted",
        Palette::border to "color.border.default",
        Palette::inputBorder to "color.input.border",
        Palette::borderHover to "color.border.hover",
        Palette::control to "color.control.hover",
        Palette::error to "color.error.default",
        Palette::errorHover to "color.error.hover",
        Palette::background to "color.bg.default",
        Palette::backgroundDisabled to "color.bg.disabled",
        Palette::backgroundHover to "color.bg.hover",
        Palette::primary to "color.primary.default",
        Palette::onPrimary to "color.primary.text",
        Palette::secondaryBorder to "color.secondary.border",
        Palette::scrim to "color.scrim",
        Palette::shadow to "color.shadow",
        Palette::focus to "color.border.focus",
        Palette::primaryHover to "color.primary.hover",
        Palette::primarySoft to "color.primary.soft",
        Palette::errorSoft to "color.error.soft",
        Palette::inputBorderHover to "color.input.border-hover",
    )

private val borderTokens = setOf("color.input.border", "color.border.default", "color.border.hover", "color.input.border-hover")

internal fun hex(color: Color): Pair<String, Double> {
    val channels = listOf(color.red, color.green, color.blue).map { (it * 255).roundToInt() }
    return "#" + channels.joinToString("") { "%02x".format(it) } to (color.alpha * 100.0).roundToInt() / 100.0
}

private fun expectColor(
    color: Color?,
    tokens: ResolvedTokens,
    path: String,
    context: String,
) {
    assertEquals("$context $path", tokens.color(path), hex(requireNotNull(color) { "$context $path" }))
}

class TokenSourceTest {
    private val palettes =
        listOf(
            Triple("light", mapOf("theme" to "light"), Palette.light),
            Triple("dark", mapOf("theme" to "dark"), Palette.dark),
            Triple("lightIncreased", mapOf("theme" to "light", "contrast" to "more"), Palette.lightIncreased),
            Triple("darkIncreased", mapOf("theme" to "dark", "contrast" to "more"), Palette.darkIncreased),
        )

    @Test
    fun palettes() {
        for ((name, context, palette) in palettes) {
            val tokens = ResolvedTokens(context)
            for ((field, path) in paletteTokens) expectColor(field(palette), tokens, path, name)
        }
    }

    @Test
    fun increasedContrastResolvesToTheContrastMoreBorders() {
        for (dark in listOf(false, true)) {
            val tokens = ResolvedTokens(mapOf("theme" to if (dark) "dark" else "light", "contrast" to "more"))
            val palette = FormhausTheme.standard.palette(dark = dark, increasedContrast = true)
            for ((field, path) in paletteTokens.filter { it.second in borderTokens }) {
                expectColor(field(palette), tokens, path, "dark=$dark")
            }
        }
    }

    @Test
    fun colourMixRules() {
        for ((name, mixes) in listOf("light" to lightColorMixes, "dark" to darkColorMixes)) {
            val tokens = ResolvedTokens(mapOf("theme" to name))
            val amounts =
                listOf(
                    mixes.primaryHover to "color.primary.hover",
                    mixes.primarySoft to "color.primary.soft",
                    mixes.errorSoft to "color.error.soft",
                )
            for ((amount, path) in amounts) {
                assertEquals("$name $path", tokens.extensions(path)["mix"]!!["amount"]!!.numberValue!!.toFloat(), amount)
            }
            val with = tokens.extensions("color.primary.hover")["mix"]!!["with"]!!.stringValue!!
            assertEquals(tokens.color(with.drop(1).dropLast(1)).first, hex(mixes.primaryHoverWith).first)
        }
    }

    @Test
    fun pointerMetrics() {
        for ((name, typography, controls) in listOf(
            Triple("fine", Typography.fine, Controls.fine),
            Triple("coarse", Typography.coarse, Controls.coarse),
        )) {
            val tokens = ResolvedTokens(mapOf("pointer" to name))
            val values =
                listOf(
                    typography.body.value to "font-size.default",
                    typography.small.value to "font-size.small",
                    typography.heading.value to "font-size.heading",
                    typography.title.value to "font-size.title",
                    controls.star.value to "star.size",
                    controls.height.value to "control.height",
                    controls.iconButton.value to "icon-button.size",
                    controls.optionImageWidth.value to "option-image.width",
                )
            for ((value, path) in values) assertEquals("$name $path", tokens.dimension(path), value.toDouble(), 0.0)
            val add = tokens.extensions("font-size.heading")["add"]!!
            assertEquals(tokens.dimension(add["amount"]!!), HEADING_STEP.toDouble(), 0.0)
        }
    }

    @Test
    fun widthMetrics() {
        for ((name, spacing) in listOf("regular" to Spacing.regular, "compact" to Spacing.compact)) {
            val tokens = ResolvedTokens(mapOf("width" to name))
            val steps =
                with(spacing) { listOf(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10) }
            steps.forEachIndexed { index, value ->
                assertEquals(tokens.dimension("space.${index + 1}"), value.value.toDouble(), 0.0)
            }
            assertEquals(tokens.dimension("gap"), spacing.gap.value.toDouble(), 0.0)
            assertEquals(name, tokens.dimension("card.padding"), spacing.cardPadding.value.toDouble(), 0.0)
            assertEquals(
                tokens.dimension("radius.default"),
                Radii.standard.control.value
                    .toDouble(),
                0.0,
            )
        }
    }

    @Test
    fun raisedShadow() {
        val tokens = ResolvedTokens()
        val value = tokens.value("shadow.raised")
        assertEquals(tokens.dimension(value["offsetX"]!!), RaisedShadow.x.value.toDouble(), 0.0)
        assertEquals(tokens.dimension(value["offsetY"]!!), RaisedShadow.y.value.toDouble(), 0.0)
        assertEquals(tokens.dimension(value["blur"]!!), RaisedShadow.blur.value.toDouble(), 0.0)
    }

    @Test
    fun pointerAndTouchThemesUseThePointerAndWidthContexts() {
        assertEquals(Typography.fine.body, FormhausTheme.pointer.typography.body)
        assertEquals(Typography.coarse.body, FormhausTheme.touch.typography.body)
        assertEquals(Spacing.regular.cardPadding, FormhausTheme.pointer.spacing.cardPadding)
        assertEquals(Spacing.compact.cardPadding, FormhausTheme.touch.spacing.cardPadding)
        assertEquals(Controls.coarse.star, FormhausTheme.touch.controls.star)
        assertEquals(48f, FormhausTheme.touch.controls.height.value)
        assertEquals(Controls.fine.height, FormhausTheme.pointer.controls.height)
        assertEquals(FormhausTheme.touch, FormhausTheme.standard)
    }
}
