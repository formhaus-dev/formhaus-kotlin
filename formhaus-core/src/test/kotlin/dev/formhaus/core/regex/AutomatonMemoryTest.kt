package dev.formhaus.core.regex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutomatonMemoryTest {
    private fun fill(pool: MemoryPool) {
        MemoryPool.current.withValue(pool) {
            val pattern = CompiledPattern("[^]*a[^]{40}$(?=)")
            assertEquals(false, pattern.test(RegexRegressionTest.randomText(400, "ab", 7L) + "b".repeat(41)))
            assertTrue(requireNotNull(pattern.automaton).bytes > 0)
        }
    }

    @Test
    @Suppress("ExplicitGarbageCollectionCall")
    fun `memory of collected automatons returns to the pool`() {
        val pool = MemoryPool()
        fill(pool)
        assertFalse(pool.take(pool.limit))
        var rounds = 0
        while (!pool.take(pool.limit) && rounds < 100) {
            System.gc()
            Thread.sleep(10)
            rounds++
        }
        assertTrue(rounds < 100)
    }

    @Test
    fun `a pattern evicted from the cache returns its memory to the pool at once`() {
        val pool = MemoryPool()
        MemoryPool.current.withValue(pool) {
            val cache = PatternCache(limit = 1)
            val source = "[^]*a[^]{40}$(?=)"
            val pattern = requireNotNull(cache.pattern(source))
            assertEquals(false, pattern.test(RegexRegressionTest.randomText(400, "ab", 7L) + "b".repeat(41)))
            assertFalse(pool.take(pool.limit))
            cache.pattern("b")
            assertTrue(pool.take(pool.limit))
        }
    }
}
