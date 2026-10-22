package dev.formhaus.compose.fields

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.URI
import java.nio.file.Paths
import java.util.UUID

internal object UploadStore {
    private const val FOLDER = "formhaus-uploads"

    private class Upload(
        val owner: Any,
        val file: File,
    )

    private val uploads = HashMap<String, Upload>()

    fun directory(cache: File): File = File(cache, FOLDER)

    suspend fun copy(
        cache: File,
        name: String,
        owner: Any,
        open: () -> InputStream?,
    ): File? {
        val folder = UUID.randomUUID().toString()
        val target = withContext(Dispatchers.IO) { copyFile(File(directory(cache), folder), name, open) } ?: return null
        uploads[folder] = Upload(owner, target)
        return target
    }

    fun owned(
        value: String,
        owner: Any,
    ): File? {
        val text = value.trim { it.isWhitespace() || it.isISOControl() }
        if (!text.lowercase().startsWith("file:")) return null
        val path = runCatching { URI(text).path }.getOrNull() ?: return null
        val normalized = runCatching { Paths.get(path).normalize() }.getOrNull() ?: return null
        val folder = normalized.parent?.fileName?.toString() ?: return null
        val upload = uploads[folder] ?: return null
        if (upload.owner !== owner || upload.file.toPath().normalize() != normalized) return null
        return upload.file
    }

    fun prune(
        owner: Any,
        keeping: Set<String>,
    ) {
        uploads.filterValues { it.owner === owner && fileValue(it.file) !in keeping }.keys.forEach(::remove)
    }

    fun release(owner: Any) {
        uploads.filterValues { it.owner === owner }.keys.forEach(::remove)
    }

    private fun remove(folder: String) {
        val upload = uploads.remove(folder) ?: return
        upload.file.parentFile?.deleteRecursively()
    }
}

internal fun fileValue(file: File): String = "file://${file.toURI().rawPath}"

private fun copyFile(
    folder: File,
    name: String,
    open: () -> InputStream?,
): File? =
    runCatching {
        if (!folder.mkdirs() && !folder.isDirectory) return@runCatching null
        val target = File(folder, name.substringAfterLast('/').ifEmpty { "file" })
        open()?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return@runCatching null
        target
    }.getOrNull()
