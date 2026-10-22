package dev.formhaus.compose.components

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AsyncGateTest {
    @Test
    fun noMoreThanTwoDecodesRunAtOnceHoweverManyImagesWait() =
        runBlocking {
            val gate = AsyncGate(2, Dispatchers.Default)
            val running = AtomicInteger()
            val highest = AtomicInteger()
            val finished = AtomicInteger()
            (0 until 64)
                .map {
                    async(Dispatchers.Default) {
                        gate.run {
                            highest.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                            repeat(8) { yield() }
                            running.decrementAndGet()
                            finished.incrementAndGet()
                        }
                    }
                }.awaitAll()
            assertTrue(highest.get() <= 2)
            assertEquals(64, finished.get())
        }
}
