package dev.formhaus.core.regex

internal class AutomatonCache(
    val plan: AutomatonPlan,
) {
    @Volatile
    private var shards: Array<AutomatonShard> = emptyArray()

    val bytes: Int get() = shards.sumOf { it.bytes }

    fun release() {
        shards.forEach(AutomatonShard::release)
    }

    fun matches(
        text: String,
        context: MatchContext,
    ): Boolean =
        run(
            { it.matches(text, context) },
            { it.matches(LazyDFA.start, text, 0, context) },
        )

    fun reverse(
        text: String,
        end: Int,
        floor: Int,
        context: MatchContext,
    ): Verdict =
        run(
            { it.reverse(text, end, floor, context) },
            { it.run(LazyDFA.entry(text, end), text, end, floor, context) },
        )

    fun acceptance(
        text: String,
        context: MatchContext,
    ): BooleanArray =
        run(
            { it.acceptance(text, context) },
            { stepper ->
                BooleanArray(text.length + 1).also { stepper.acceptance(LazyDFA.start, text, 0, it, context) }
            },
        )

    private inline fun <Value> run(
        cached: (LazyDFA) -> Value,
        uncached: (ThreadStep) -> Value,
    ): Value {
        val current = shards
        if (current.isNotEmpty()) {
            var index = ((Thread.currentThread().id * SPREAD) ushr 61).toInt() % current.size
            repeat(current.size) {
                val shard = current[index]
                if (shard.acquire()) return shard.holding(cached)
                index = if (index + 1 == current.size) 0 else index + 1
            }
        }
        val added = addShard()
        if (added != null && added.acquire()) return added.holding(cached)
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
