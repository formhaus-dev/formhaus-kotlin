package dev.formhaus.compose.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal const val MAX_IMAGE_SIDE = 2048
internal const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
private const val REQUEST_TIMEOUT = 15_000
private const val RESOURCE_TIMEOUT = 30_000L
private const val CHUNK = 64 * 1024
private val decodes = AsyncGate(2, Dispatchers.Default)
private val fetches = AsyncGate(4, Dispatchers.IO)
private val imageTypes = setOf("image/png", "image/jpeg", "image/gif", "image/webp")

internal fun isInline(source: String): Boolean = source.startsWith("data:image/")

internal fun remoteImageUrl(source: String): URL? {
    val uri = runCatching { URI(source) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if ((scheme != "https" && scheme != "http") || uri.host.isNullOrEmpty() || uri.rawUserInfo != null) return null
    return runCatching { uri.toURL() }.getOrNull()
}

internal suspend fun loadImage(source: String): Bitmap? {
    if (isInline(source)) return decodes.run { decodeInline(source) }
    val url = remoteImageUrl(source) ?: return null
    val data = fetches.run { withTimeoutOrNull(RESOURCE_TIMEOUT) { fetchImage(url) } } ?: return null
    return decodes.run { decodeImage(data) }
}

private fun fetchImage(url: URL): ByteArray? {
    val connection = url.openConnection() as? HttpURLConnection ?: return null
    connection.useCaches = false
    connection.connectTimeout = REQUEST_TIMEOUT
    connection.readTimeout = REQUEST_TIMEOUT
    return try {
        val status = runCatching { connection.responseCode }.getOrDefault(-1)
        if (status !in 200..299 || connection.contentLengthLong > MAX_IMAGE_BYTES) null else readCapped(connection)
    } finally {
        connection.disconnect()
    }
}

private fun readCapped(connection: HttpURLConnection): ByteArray? =
    runCatching {
        connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(CHUNK)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (output.size() + read > MAX_IMAGE_BYTES) return@use null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }.getOrNull()

private fun decodeInline(source: String): Bitmap? {
    val comma = source.indexOf(',')
    if (!isInline(source) || comma < 0 || !source.substring(0, comma).endsWith(";base64")) return null
    val data = runCatching { Base64.decode(source.substring(comma + 1), Base64.DEFAULT) }.getOrNull() ?: return null
    return decodeImage(data)
}

internal fun imagePixelSize(data: ByteArray): Pair<Int, Int>? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    if (bounds.outMimeType !in imageTypes || bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    return bounds.outWidth to bounds.outHeight
}

internal fun decodeImage(data: ByteArray): Bitmap? {
    if (data.size > MAX_IMAGE_BYTES) return null
    val (width, height) = imagePixelSize(data) ?: return null
    if (width > MAX_IMAGE_SIDE || height > MAX_IMAGE_SIDE) return null
    return BitmapFactory.decodeByteArray(data, 0, data.size)
}
