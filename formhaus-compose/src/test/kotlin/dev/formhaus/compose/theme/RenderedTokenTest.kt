package dev.formhaus.compose.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderedTokenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aRenderedLightInputBorderPixelEqualsTheTokenHex() = expectRendered("light", Palette.light)

    @Test
    fun aRenderedDarkInputBorderPixelEqualsTheTokenHex() = expectRendered("dark", Palette.dark)

    private fun expectRendered(
        name: String,
        palette: Palette,
    ) {
        compose.setContent { Box(Modifier.size(4.dp).background(palette.inputBorder)) }
        val pixel = compose.onRoot().captureToImage().toPixelMap()[1, 1]
        assertEquals(name, ResolvedTokens(mapOf("theme" to name)).color("color.input.border"), hex(pixel))
    }
}
