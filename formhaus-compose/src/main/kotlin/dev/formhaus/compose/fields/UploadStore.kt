package dev.formhaus.compose.fields

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

internal const val MAX_UPLOAD_BYTES: Long = 100L * 1024 * 1024
private const val CHUNK = 64 * 1024

internal object UploadStore {
    private const val FOLDER = "formhaus-uploads"

    private class Upload(
        val owner: Long,
        val file: File,
    )

    private val uploads = HashMap<String, Upload>()
    private val lock = Mutex()
    private var swept = false

    fun directory(cache: File): File = File(cache, FOLDER)

    suspend fun copy(
        cache: File,
        owner: Long,
        name: () -> String,
        open: () -> InputStream?,
    ): File? =
        lock.withLock {
            val root = directory(cache)
            val folder = UUID.randomUUID().toString()
            val sweep = !swept
            swept = true
            val keep = uploads.keys.toSet()
            val target =
                try {
                    withContext(Dispatchers.IO) {
                        if (sweep) sweepStale(root, keep)
                        copyFile(File(root, folder), name, open)
                    }
                } catch (error: CancellationException) {
                    File(root, folder).deleteRecursively()
                    throw error
                }
            target?.also { uploads[folder] = Upload(owner, it) }
        }

    fun owned(
        value: String,
        owner: Long,
    ): File? {
        val (folder, path) = location(value) ?: return null
        val upload = uploads[folder] ?: return null
        if (upload.owner != owner || upload.file.toPath().normalize() != path) return null
        return upload.file
    }

    fun isUpload(value: String): Boolean = location(value)?.second?.inUploads == true

    fun adopt(
        value: String,
        owner: Long,
    ): Boolean {
        val (folder, path) = location(value)?.takeIf { it.second.inUploads } ?: return false
        val upload = uploads[folder]
        if (upload != null) return upload.owner == owner && upload.file.toPath().normalize() == path
        val file = path.toFile()
        if (!file.isFile) return false
        uploads[folder] = Upload(owner, file)
        return true
    }

    private val Path.inUploads: Boolean get() = parent?.parent?.fileName?.toString() == FOLDER

    fun discard(
        value: String,
        owner: Long,
    ) {
        val folder = location(value)?.first ?: return
        if (uploads[folder]?.owner == owner) remove(folder)
    }

    fun prune(
        owner: Long,
        keeping: Set<String>,
    ) {
        uploads.filterValues { it.owner == owner && fileValue(it.file) !in keeping }.keys.forEach(::remove)
    }

    fun release(owner: Long) {
        uploads.filterValues { it.owner == owner }.keys.forEach(::remove)
    }

    private fun remove(folder: String) {
        val upload = uploads.remove(folder) ?: return
        upload.file.parentFile?.deleteRecursively()
    }

    private fun location(value: String): Pair<String, Path>? {
        val text = value.trim { it.isWhitespace() || it.isISOControl() }
        if (!text.lowercase().startsWith("file:")) return null
        val path = runCatching { URI(text).path }.getOrNull() ?: return null
        val normalized = runCatching { Paths.get(path).normalize() }.getOrNull() ?: return null
        val folder = normalized.parent?.fileName?.toString() ?: return null
        return folder to normalized
    }
}

internal fun fileValue(file: File): String = "file://${file.toURI().rawPath}"

private fun sweepStale(
    root: File,
    keep: Set<String>,
) {
    root.listFiles()?.filter { it.name !in keep }?.forEach { it.deleteRecursively() }
}

private suspend fun copyFile(
    folder: File,
    name: () -> String,
    open: () -> InputStream?,
): File? {
    var copied = false
    try {
        if (!folder.mkdirs() && !folder.isDirectory) return null
        val target = File(folder, uploadName(name()))
        copied = open()?.use { input -> target.outputStream().use { copyCapped(input, it) } } == true
        return target.takeIf { copied }
    } catch (_: IOException) {
        return null
    } catch (_: SecurityException) {
        return null
    } finally {
        if (!copied) folder.deleteRecursively()
    }
}

private suspend fun copyCapped(
    input: InputStream,
    output: OutputStream,
): Boolean {
    val buffer = ByteArray(CHUNK)
    var total = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = input.read(buffer)
        if (read < 0) return true
        total += read
        if (total > MAX_UPLOAD_BYTES) return false
        output.write(buffer, 0, read)
    }
}
