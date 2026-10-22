package dev.formhaus.core.regex

internal class AutomatonCache(
    private val plan: AutomatonPlan,
) {
    @Volatile
    private var shards: List<AutomatonShard> = emptyList()

    val bytes: Int get() = shards.sumOf { it.bytes }

    fun release() {
        shards.forEach(AutomatonShard::release)
    }

    fun matches(
        text: CharArray,
        context: MatchContext,
    ): Boolean =
        run(
            { it.matches(text, context) },
            { it.matches(LazyDFA.start, text, 0, context) },
        )

    fun reverse(
        text: CharArray,
        end: Int,
        floor: Int,
        context: MatchContext,
    ): Verdict =
        run(
            { it.reverse(text, end, floor, context) },
            { it.run(LazyDFA.entry(text, end), text, end, floor, context) },
        )

    fun acceptance(
        text: CharArray,
        context: MatchContext,
    ): BooleanArray =
        run(
            { it.acceptance(text, context) },
            { stepper ->
                BooleanArray(text.size + 1).also { stepper.acceptance(LazyDFA.start, text, 0, it, context) }
            },
        )

    private fun <Value> run(
        cached: (LazyDFA) -> Value,
        uncached: (ThreadStep) -> Value,
    ): Value {
        val current = shards
        val start = ((Thread.currentThread().id * SPREAD) ushr 61).toInt()
        for (offset in current.indices) {
            current[(start + offset) % current.size].run(cached)?.let { return it.value }
        }
        addShard()?.run(cached)?.let { return it.value }
        return uncached(plan.stepper())
    }

    private fun addShard(): AutomatonShard? =
        synchronized(this) {
            if (shards.size >= SHARD_COUNT) return null
            val shard = AutomatonShard(plan)
            shards = shards + shard
            shard
        }

    private companion object {
        const val SHARD_COUNT = 8
        const val SPREAD = -7046029254386353131L
    }
}
