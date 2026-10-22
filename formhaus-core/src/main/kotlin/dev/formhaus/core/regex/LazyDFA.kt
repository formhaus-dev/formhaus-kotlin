package dev.formhaus.core.regex

internal class AutomatonPlan(
    val program: RegexProgram,
    val anchored: Boolean,
    val first: CharSet? = null,
    val backward: Boolean = false,
    val scanning: Boolean = false,
    val pool: MemoryPool = MemoryPool.current.value,
) {
    val classes: UnitClasses = UnitClasses(program)

    fun stepper(): ThreadStep = ThreadStep(program, anchored, backward, scanning)
}

internal class LazyDFA(
    private val plan: AutomatonPlan,
    private val memory: AutomatonMemory,
) {
    class Key(
        val pcs: IntArray,
        val context: UnitContext,
    ) {
        override fun equals(other: Any?): Boolean = other is Key && context == other.context && pcs.contentEquals(other.pcs)

        override fun hashCode(): Int = 31 * pcs.contentHashCode() + context.hashCode()
    }

    private class Move(
        val accepts: Boolean,
        val state: Int,
        val threads: Key? = null,
    )

    private val classes = plan.classes
    private val width = classes.count + 1
    private val stepper = plan.stepper()
    private val keys = ArrayList<Key>()
    private val lookup = HashMap<Key, Int>()
    private var idle = BooleanArray(0)
    private var table = IntArray(0)
    private var accepting = BooleanArray(0)
    private var resets = 0
    private var mayReset = true
    private var walkState = 0

    init {
        append(start)
    }

    fun release() {
        memory.release()
    }

    fun matches(
        text: CharArray,
        context: MatchContext,
    ): Boolean {
        mayReset = true
        var state = 0
        var position = 0
        val first = plan.first
        val skipping = first != null && !stepper.anchored
        while (position < text.size) {
            position = walk(text, state, position, skipping)
            state = walkState
            if (position >= text.size) break
            if (first != null && idle[state]) {
                val next = firstCandidate(text, position, first)
                if (next == text.size) return false
                if (next != position) {
                    position = next
                    val key = Key(intArrayOf(0), UnitContext.of(text[next - 1].code))
                    state = intern(key) ?: return stepper.matches(key, text, position, context)
                }
            }
            val move = step(state, text[position].code, position, context)
            if (move.accepts) return true
            move.threads?.let { return stepper.matches(it, text, position + 1, context) }
            if (move.state == STOP) return false
            state = move.state
            position++
        }
        return step(state, -1, position, context).accepts
    }

    private fun walk(
        text: CharArray,
        from: Int,
        origin: Int,
        skipping: Boolean,
    ): Int {
        var state = from
        var position = origin
        while (position < text.size && (!skipping || !idle[state])) {
            val next = table[state * width + classes.index(text[position])]
            if (next < 0) break
            state = next
            position++
        }
        walkState = state
        return position
    }

    private fun firstCandidate(
        text: CharArray,
        from: Int,
        first: CharSet,
    ): Int {
        var next = from
        while (next < text.size && text[next] !in first) next++
        return next
    }

    fun acceptance(
        text: CharArray,
        context: MatchContext,
    ): BooleanArray {
        mayReset = true
        val accepted = BooleanArray(text.size + 1)
        var state = 0
        for (offset in 0..text.size) {
            val position = if (stepper.backward) text.size - offset else offset
            val move = step(state, text.nextUnit(position, stepper.backward), position, context)
            accepted[position] = move.accepts
            move.threads?.let {
                stepper.acceptance(it, text, offset + 1, accepted, context)
                return accepted
            }
            if (move.state == STOP) return accepted
            state = move.state
        }
        return accepted
    }

    fun reverse(
        text: CharArray,
        end: Int,
        floor: Int,
        context: MatchContext,
    ): Verdict {
        mayReset = true
        val key = entry(text, end)
        var state = intern(key) ?: return stepper.run(key, text, end, floor, context)
        var position = end
        while (true) {
            while (position > floor) {
                val next = table[state * width + classes.index(text[position - 1])]
                if (next < 0) break
                state = next
                position--
            }
            val move = step(state, text.nextUnit(position, backward = true), position, context)
            if (move.accepts) return Verdict.MATCH
            if (move.threads == null && move.state == STOP) return Verdict.MISS
            if (position == floor) return Verdict.UNDECIDED
            move.threads?.let { return stepper.run(it, text, position - 1, floor, context) }
            state = move.state
            position--
        }
    }

    private fun step(
        state: Int,
        unit: Int,
        position: Int,
        context: MatchContext,
    ): Move {
        val slot = state * width + (if (unit < 0) classes.count else classes.index(unit.toChar()))
        val target = table[slot]
        if (target >= 0 || target == STOP) return Move(accepting[slot], target)
        val key = keys[state]
        val advance = stepper.advance(key.pcs, key.context, unit, position, context)
        if (unit < 0 || advance.next.isEmpty() || (advance.accepts && !stepper.scanning)) {
            record(slot, STOP, advance)
            return Move(advance.accepts, STOP)
        }
        val pcs = advance.next.toArray()
        pcs.sort()
        val next = Key(pcs, UnitContext.of(unit))
        val generation = resets
        val id = intern(next) ?: return Move(advance.accepts, STOP, next)
        if (generation == resets) record(slot, id, advance)
        return Move(advance.accepts, id)
    }

    private fun record(
        slot: Int,
        target: Int,
        advance: ThreadStep.Advance,
    ) {
        table[slot] = if (advance.volatile) VOLATILE else target
        accepting[slot] = advance.accepts
    }

    private fun intern(key: Key): Int? {
        lookup[key]?.let { return it }
        val cost = 4 * key.pcs.size + 5 * width + 96
        if (memory.reserve(cost)) return append(key)
        if (!mayReset) return null
        mayReset = false
        reset()
        return if (memory.reserve(cost)) append(key) else null
    }

    private fun reset() {
        keys.clear()
        lookup.clear()
        memory.release()
        resets++
        append(start)
    }

    private fun append(key: Key): Int {
        val state = keys.size
        if (state == idle.size) {
            val capacity = maxOf(16, 2 * state)
            idle = idle.copyOf(capacity)
            table = table.copyOf(capacity * width)
            accepting = accepting.copyOf(capacity * width)
        }
        keys.add(key)
        lookup[key] = state
        idle[state] = !stepper.anchored && key.pcs.size == 1 && key.pcs[0] == 0
        table.fill(UNKNOWN, state * width, (state + 1) * width)
        accepting.fill(false, state * width, (state + 1) * width)
        return state
    }

    companion object {
        val start: Key = Key(intArrayOf(0), UnitContext.EDGE)
        private const val UNKNOWN = -1
        private const val STOP = -2
        private const val VOLATILE = -3

        fun entry(
            text: CharArray,
            end: Int,
        ): Key = Key(intArrayOf(0), if (end < text.size) UnitContext.of(text[end].code) else UnitContext.EDGE)
    }
}
