package dev.formhaus.compose.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

internal open class ImageCache(
    private val maxCount: Int,
    private val maxBytes: Int,
    private val loader: suspend (String) -> Bitmap?,
) {
    private class Entry(
        val image: ImageBitmap,
        val bytes: Int,
    )

    private val entries = LinkedHashMap<String, Entry>(INITIAL_CAPACITY, LOAD_FACTOR, true)
    private val pending = HashMap<String, Deferred<ImageBitmap?>>()
    private var bytes = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun cached(source: String): ImageBitmap? = entries[source]?.image

    fun prefetch(sources: List<String>) {
        sources.filter(::isInline).forEach(::request)
    }

    fun request(source: String): Deferred<ImageBitmap?> {
        cached(source)?.let { return CompletableDeferred(it) }
        if (!loadable(source)) return CompletableDeferred(null)
        pending[source]?.let { return it }
        val job =
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    fetch(source)
                } finally {
                    pending.remove(source)
                }
            }
        pending[source] = job
        job.start()
        return job
    }

    suspend fun image(source: String): ImageBitmap? = cached(source) ?: if (loadable(source)) fetch(source) else null

    private suspend fun fetch(source: String): ImageBitmap? {
        val bitmap = loader(source) ?: return null
        return cached(source) ?: store(source, bitmap)
    }

    private fun loadable(source: String): Boolean = isInline(source) || remoteImageUrl(source) != null

    private fun store(
        source: String,
        bitmap: Bitmap,
    ): ImageBitmap {
        val entry = Entry(bitmap.asImageBitmap(), bitmap.allocationByteCount)
        entries[source] = entry
        bytes += entry.bytes
        val oldest = entries.entries.iterator()
        while ((entries.size > maxCount || bytes > maxBytes) && entries.size > 1) {
            bytes -= oldest.next().value.bytes
            oldest.remove()
        }
        return entry.image
    }
}

internal object OptionImages : ImageCache(256, 48 * 1024 * 1024, ::loadImage)

@Composable
internal fun ImageCache.shown(source: String): ImageBitmap? {
    var held by remember(source) { mutableStateOf(cached(source)) }
    if (held == null) LaunchedEffect(source) { held = request(source).await() }
    return held
}

private const val INITIAL_CAPACITY = 16
private const val LOAD_FACTOR = 0.75f
