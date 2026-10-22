package dev.formhaus.compose.components

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch

private const val SECOND = 1_000_000_000L

private class TrickleStream(
    private val clock: LongArray,
) : InputStream() {
    var reads = 0

    override fun read(): Int = throw UnsupportedOperationException()

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        reads += 1
        clock[0] += SECOND
        buffer[offset] = 1
        return 1
    }
}

private class StalledStream : InputStream() {
    private val closed = CountDownLatch(1)

    override fun read(): Int = throw UnsupportedOperationException()

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        closed.await()
        throw IOException("closed")
    }

    override fun close() {
        closed.countDown()
    }
}

class ImageDeadlineTest {
    @Test
    fun aSlowStreamStopsAtTheDeadline() {
        val clock = longArrayOf(0)
        val stream = TrickleStream(clock)
        assertNull(readCapped(stream, 5 * SECOND) { clock[0] })
        assertEquals(5, stream.reads)
    }

    @Test
    fun aStreamThatEndsInTimeIsReadWhole() {
        val data = ByteArray(200_000) { it.toByte() }
        assertArrayEquals(data, readCapped(ByteArrayInputStream(data), Long.MAX_VALUE) { 0 })
    }

    @Test(timeout = 10_000)
    fun theWatchdogAbortsABlockedRead() {
        val stream = StalledStream()
        val result = withDeadline(50, stream::close) { deadline -> runCatching { readCapped(stream, deadline) }.getOrNull() }
        assertNull(result)
    }

    @Test
    fun aFinishedBodyCancelsTheWatchdog() {
        var aborted = false
        assertTrue(withDeadline(50, { aborted = true }) { true })
        Thread.sleep(150)
        assertFalse(aborted)
    }
}
