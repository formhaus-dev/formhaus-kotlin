package dev.formhaus.compose.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class ImageEntry {
    var image: ImageBitmap? by mutableStateOf(null)
    var loading = false
    var bytes = 0
}

internal object OptionImages {
    private const val MAX_BYTES = 48 * 1024 * 1024
    private const val MAX_COUNT = 256
    private val entries = HashMap<String, ImageEntry>()
    private val order = ArrayDeque<String>()
    private var bytes = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun entry(source: String): ImageEntry = entries.getOrPut(source, ::ImageEntry)

    fun cached(source: String): ImageBitmap? = entries[source]?.image

    fun loaded(source: String): ImageBitmap? = if (loadable(source)) entry(source).image else null

    fun prefetch(sources: List<String>) {
        sources.filter(::isInline).forEach(::load)
    }

    fun load(source: String) {
        if (!loadable(source)) return
        val entry = entry(source)
        if (entry.image != null || entry.loading) return
        entry.loading = true
        scope.launch {
            image(source)
            entry.loading = false
        }
    }

    suspend fun image(source: String): ImageBitmap? {
        val entry = entry(source)
        entry.image?.let { return it }
        val bitmap = loadImage(source) ?: return null
        if (entry.image == null) store(bitmap.asImageBitmap(), bitmap.allocationByteCount, source, entry)
        return entry.image
    }

    private fun loadable(source: String): Boolean = isInline(source) || remoteImageUrl(source) != null

    private fun store(
        image: ImageBitmap,
        size: Int,
        source: String,
        entry: ImageEntry,
    ) {
        entry.image = image
        entry.bytes = size
        bytes += size
        order.addLast(source)
        while ((order.size > MAX_COUNT || bytes > MAX_BYTES) && order.size > 1) {
            entries.remove(order.removeFirst())?.let { bytes -= it.bytes }
        }
    }
}
