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
import java.nio.file.Files
import java.nio.file.LinkOption
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

    @Volatile
    private var root: File? = null
    private var attached: File? = null

    fun directory(cache: File): File = File(cache, FOLDER)

    fun attach(cache: File) {
        if (attached == cache) return
        attached = cache
        root = runCatching { directory(cache).canonicalFile }.getOrNull()
    }

    suspend fun copy(
        cache: File,
        owner: Long,
        name: () -> String,
        open: () -> InputStream?,
    ): File? =
        lock.withLock {
            attach(cache)
            val folder = File(root ?: return@withLock null, UUID.randomUUID().toString())
            val target =
                try {
                    withContext(Dispatchers.IO) { copyFile(folder, name, open) }
                } catch (error: CancellationException) {
                    deleteFolder(folder)
                    throw error
                }
            target?.also { uploads[folder.name] = Upload(owner, it) }
        }

    fun owned(
        value: String,
        owner: Long,
    ): File? {
        val file = location(value) ?: return null
        return registered(file)?.takeIf { it.owner == owner }?.file
    }

    fun isUpload(value: String): Boolean = location(value) != null

    fun exists(value: String): Boolean = location(value)?.let(::isPlainFile) == true

    fun adopt(
        value: String,
        owner: Long,
    ): Boolean {
        val file = location(value)?.takeIf(::isPlainFile) ?: return false
        val upload = uploads[file.parentFile?.name]
        if (upload != null) return upload.owner == owner && upload.file == file
        uploads[file.parentFile?.name ?: return false] = Upload(owner, file)
        return true
    }

    fun discard(
        value: String,
        owner: Long,
    ) {
        val file = location(value) ?: return
        if (registered(file)?.owner == owner) remove(file.parentFile?.name ?: return)
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

    private fun registered(file: File): Upload? = uploads[file.parentFile?.name]?.takeIf { it.file == file }

    private fun remove(folder: String) {
        val upload = uploads.remove(folder) ?: return
        val directory = upload.file.parentFile ?: return
        if (location(fileValue(upload.file)) == upload.file && directory.name == folder) deleteFolder(directory)
    }

    private fun location(value: String): File? {
        val base = root ?: return null
        val text = value.trim { it.isWhitespace() || it.isISOControl() }
        if (!text.lowercase().startsWith("file:")) return null
        val path = runCatching { URI(text).path }.getOrNull() ?: return null
        val file = runCatching { Paths.get(path).normalize().toFile() }.getOrNull() ?: return null
        val folder = file.parentFile ?: return null
        return file.takeIf { folder.parentFile == base && isUuid(folder.name) && file.name.isNotEmpty() }
    }
}

private fun isUuid(name: String): Boolean = runCatching { UUID.fromString(name).toString() == name }.getOrDefault(false)

private fun isPlainFile(file: File): Boolean {
    val path = file.toPath()
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false
    return runCatching { file.canonicalFile == file }.getOrDefault(false)
}

private fun deleteFolder(folder: File) {
    val path = folder.toPath()
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) return
    runCatching {
        Files.newDirectoryStream(path).use { entries -> entries.forEach { Files.deleteIfExists(it) } }
        Files.deleteIfExists(path)
    }
}

internal fun fileValue(file: File): String = "file://${file.toURI().rawPath}"

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
        if (!copied) deleteFolder(folder)
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
