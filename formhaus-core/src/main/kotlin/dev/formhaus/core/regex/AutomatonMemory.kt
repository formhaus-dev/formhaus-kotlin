package dev.formhaus.core.regex

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private class ShardReference(
    shard: AutomatonShard,
    queue: ReferenceQueue<AutomatonShard>,
    val memory: AutomatonMemory,
) : WeakReference<AutomatonShard>(shard, queue)

internal class MemoryPool(
    val limit: Int = AutomatonMemory.GLOBAL_LIMIT,
) {
    private var total = 0
    private val queue = ReferenceQueue<AutomatonShard>()
    private val registry: MutableSet<ShardReference> = ConcurrentHashMap.newKeySet()

    val reserved: Int get() = synchronized(this) { total }

    fun take(bytes: Int): Boolean {
        drain()
        synchronized(this) {
            if (total + bytes > limit) return false
            total += bytes
            return true
        }
    }

    fun give(bytes: Int) {
        synchronized(this) { total -= bytes }
    }

    fun register(
        shard: AutomatonShard,
        memory: AutomatonMemory,
    ) {
        registry.add(ShardReference(shard, queue, memory))
    }

    fun reclaim(): Boolean {
        drain()
        val shards = registry.mapNotNull { it.get() }
        return shards.any { it.evict(sparingRecent = true) } || shards.any { it.evict(sparingRecent = false) }
    }

    private fun drain() {
        while (true) {
            val dead = queue.poll() as? ShardReference ?: return
            registry.remove(dead)
            dead.memory.release()
        }
    }

    companion object {
        val shared: MemoryPool = MemoryPool()
        val current: ScopedLocal<MemoryPool> = ScopedLocal(shared)
    }
}

internal class AutomatonMemory(
    val pool: MemoryPool,
) {
    @Volatile
    var used: Int = 0
        private set
    private var reserved = 0

    fun reserve(bytes: Int): Boolean {
        if (used + bytes > PATTERN_LIMIT) return false
        if (used + bytes > reserved) {
            val grant = minOf(maxOf(CHUNK, used + bytes - reserved), PATTERN_LIMIT - reserved)
            if (!pool.take(grant) && !(pool.reclaim() && pool.take(grant))) return false
            reserved += grant
        }
        used += bytes
        return true
    }

    fun release() {
        pool.give(reserved)
        used = 0
        reserved = 0
    }

    companion object {
        const val PATTERN_LIMIT: Int = 1 shl 20
        const val GLOBAL_LIMIT: Int = 32 shl 20
        private const val CHUNK = 4 shl 10

        val reservedTotal: Int get() = MemoryPool.current.value.reserved
    }
}

internal class AutomatonShard(
    private val plan: AutomatonPlan,
) {
    class Ran<Value>(
        val value: Value,
    )

    private val memory = AutomatonMemory(plan.pool)
    private val lock = AtomicBoolean(false)
    private var automaton = LazyDFA(plan, memory)
    private var recent = false

    init {
        plan.pool.register(this, memory)
    }

    val bytes: Int get() = memory.used

    fun <Value> run(body: (LazyDFA) -> Value): Ran<Value>? {
        if (!lock.compareAndSet(false, true)) return null
        try {
            recent = true
            return Ran(body(automaton))
        } finally {
            lock.set(false)
        }
    }

    fun evict(sparingRecent: Boolean): Boolean {
        if (!lock.compareAndSet(false, true)) return false
        try {
            if (memory.used == 0) return false
            if (sparingRecent && recent) {
                recent = false
                return false
            }
            automaton.release()
            automaton = LazyDFA(plan, memory)
            return true
        } finally {
            lock.set(false)
        }
    }
}
