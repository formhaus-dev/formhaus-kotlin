package dev.formhaus.core.regex

internal object LookOutcome {
    fun of(
        look: Int,
        holds: Boolean,
    ): Int = 2 * look + if (holds) 1 else 0
}

internal class LookMemo(
    private val outcomes: IntArray,
    val target: Int,
    val accepts: Boolean,
) {
    val bytes: Int get() = 4 * outcomes.size + OVERHEAD

    fun replays(
        context: MatchContext,
        position: Int,
    ): Boolean {
        for (outcome in outcomes) {
            if (context.holds(outcome ushr 1, position) != (outcome and 1 == 1)) return false
        }
        return true
    }

    private companion object {
        const val OVERHEAD = 48
    }
}
