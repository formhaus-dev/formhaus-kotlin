package dev.formhaus.compose.components

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val FIRST = "https://example.com/a.png"
private const val SECOND = "https://example.com/b.png"

@RunWith(RobolectricTestRunner::class)
class ImageCacheTest {
    @get:Rule
    val compose = createComposeRule()

    private val loads = mutableListOf<String>()
    private var failing = false

    private val cache =
        ImageCache(1, Int.MAX_VALUE) { source ->
            loads += source
            if (failing) null else Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

    @Test
    fun anOnScreenImageReloadsAfterItsEntryIsEvicted() {
        var shown: Any? = null
        compose.setContent { shown = cache.shown(FIRST) }
        compose.waitForIdle()
        assertNotNull(shown)
        compose.runOnIdle { runBlocking { cache.image(SECOND) } }
        compose.waitForIdle()
        assertNotNull(shown)
        assertEquals(listOf(FIRST, SECOND, FIRST), loads)
    }

    @Test
    fun aFailedImageLeavesNoEntryBehind() {
        failing = true
        runBlocking { assertNull(cache.image(FIRST)) }
        assertNull(cache.cached(FIRST))
        failing = false
        runBlocking { assertNotNull(cache.image(FIRST)) }
        assertEquals(listOf(FIRST, FIRST), loads)
    }
}
