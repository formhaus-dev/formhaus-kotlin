package dev.formhaus.compose.components

import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.formhaus.compose.fields.FieldList
import dev.formhaus.compose.fields.Themed
import dev.formhaus.compose.fields.definition
import dev.formhaus.compose.gallery.SWATCH
import dev.formhaus.compose.model.model
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InlineImageTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dataImagesDecodeOnceAndComeFromTheCache() =
        runBlocking {
            val image = OptionImages.image(SWATCH)
            assertNotNull(image)
            assertSame(image, OptionImages.cached(SWATCH))
            assertSame(image, OptionImages.image(SWATCH))
            assertNull(OptionImages.image("data:image/png;base64,AA=="))
        }

    @Test
    fun sourcesThatDifferOnlyInTheirTypeNeverShareACacheEntry() =
        runBlocking {
            val pictured = SWATCH.replace("image/png", "image/x-png")
            val decoded = OptionImages.image(pictured)
            assertNotNull(decoded)
            assertNotSame(OptionImages.cached(SWATCH), decoded)
        }

    @Test
    fun pictureOptionsPrefetchTheirInlineImages() {
        val source = SWATCH.replace("image/png", "image/png;name=prefetch")
        val option = """{"value": "a", "label": "A", "image": "$source"}"""
        val form = model(definition("""{"key": "c", "type": "radio", "label": "C", "options": [$option]}"""))
        compose.setContent { Themed { FieldList(form) } }
        compose.waitUntil(5_000) { OptionImages.cached(source) != null }
        assertNotEquals(null, OptionImages.cached(source))
    }
}
