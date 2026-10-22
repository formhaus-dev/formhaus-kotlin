package dev.formhaus.compose.components

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

private const val CHUNK = 64 * 1024

private val watchdog =
    ScheduledThreadPoolExecutor(1) { task -> Thread(task, "formhaus-image-deadline").apply { isDaemon = true } }
        .apply { removeOnCancelPolicy = true }

internal fun <T> withDeadline(
    timeoutMillis: Long,
    abort: () -> Unit,
    body: (deadline: Long) -> T,
): T {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    val timer = watchdog.schedule(abort, timeoutMillis, TimeUnit.MILLISECONDS)
    try {
        return body(deadline)
    } finally {
        timer.cancel(false)
    }
}

internal fun readCapped(
    input: InputStream,
    deadline: Long,
    now: () -> Long = System::nanoTime,
): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(CHUNK)
    while (now() - deadline < 0) {
        val read = input.read(buffer)
        if (read < 0) return output.toByteArray()
        if (output.size() + read > MAX_IMAGE_BYTES) return null
        output.write(buffer, 0, read)
    }
    return null
}
