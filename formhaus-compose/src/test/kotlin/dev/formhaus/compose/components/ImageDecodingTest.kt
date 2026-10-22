package dev.formhaus.compose.components

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

private fun encoded(
    width: Int,
    height: Int,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.rgb(51, 102, 153))
    return ByteArrayOutputStream().also { bitmap.compress(format, 90, it) }.toByteArray()
}

private fun bmp(
    width: Int,
    height: Int,
): ByteArray {
    val row = (width * 3 + 3) / 4 * 4
    val size = 54 + row * height
    return ByteBuffer
        .allocate(size)
        .order(ByteOrder.LITTLE_ENDIAN)
        .put('B'.code.toByte())
        .put('M'.code.toByte())
        .putInt(size)
        .putInt(0)
        .putInt(54)
        .putInt(40)
        .putInt(width)
        .putInt(height)
        .putShort(1)
        .putShort(24)
        .putInt(0)
        .putInt(row * height)
        .putInt(2835)
        .putInt(2835)
        .putInt(0)
        .putInt(0)
        .array()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageDecodingTest {
    @Test
    fun smallPngJpegAndWebpImagesDecode() {
        for (format in listOf(Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.JPEG, Bitmap.CompressFormat.WEBP_LOSSLESS)) {
            val image = requireNotNull(decodeImage(encoded(8, 6, format)))
            assertEquals(8 to 6, image.width to image.height)
        }
    }

    @Test
    fun imagesWiderOrTallerThanTheLimitAreRefusedFromTheirHeader() {
        val wide = encoded(MAX_IMAGE_SIDE + 1, 1)
        assertEquals(MAX_IMAGE_SIDE + 1, imagePixelSize(wide)?.first)
        assertNull(decodeImage(wide))
        assertNull(decodeImage(encoded(1, MAX_IMAGE_SIDE + 1)))
        assertNotNull(decodeImage(encoded(MAX_IMAGE_SIDE, 1)))
    }

    @Test
    fun otherFormatsAndOversizedPayloadsAreRefused() {
        assertNull(decodeImage(bmp(4, 4)))
        assertNull(decodeImage("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray()))
        assertNull(decodeImage(encoded(4, 4) + ByteArray(MAX_IMAGE_BYTES)))
    }

    @Test
    fun onlyHttpAndHttpsUrlsWithoutAUserNameAreFetched() {
        assertNotNull(remoteImageUrl("HTTPS://example.com/a.png"))
        assertNotNull(remoteImageUrl("http://localhost:5173/a.png"))
        val refused =
            listOf("https://h\\@evil.example/x.png", "https://u@example.com/x.png", "file:///tmp/a.png", "/assets/a.png", "https:///a.png")
        assertTrue(refused.all { remoteImageUrl(it) == null })
    }
}
