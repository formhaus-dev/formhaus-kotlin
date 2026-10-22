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
    val looksAround: Boolean = program.instructions.any { it is Instruction.Look }

    fun stepper(): ThreadStep = ThreadStep(program, anchored, backward, scanning)
}

internal class LazyDFA(
    private val plan: AutomatonPlan,
    val memory: AutomatonMemory,
) {
    class Key(
        val pcs: IntArray,
        val context: UnitContext,
    ) {
        override fun equals(other: Any?): Boolean = other is Key && context == other.context && pcs.contentEquals(other.pcs)

        override fun hashCode(): Int = 31 * pcs.contentHashCode() + context.hashCode()
    }

    private val classes = plan.classes
    private val width = classes.count + 1
    private val stepper = plan.stepper()
    private val keys = ArrayList<Key>()
    private val lookup = HashMap<Key, Int>()
    private var idle = BooleanArray(0)
    private var table = IntArray(0)
    private var accepting = BooleanArray(0)
    private var memos = arrayOfNulls<LookMemo>(0)
    private val tracksLooks = plan.looksAround
    private val slotBytes = if (tracksLooks) 9 else 5
    private var resets = 0
    private var mayReset = true
    private var walkState = 0
    private var accepts = false
    private var overflow: Key? = null

    init {
        append(start)
    }

    fun release() {
        memory.release()
    }

    fun matches(
        text: String,
        context: MatchContext,
    ): Boolean {
        mayReset = true
        var state = 0
        var position = 0
        val first = plan.first
        val skipping = first != null && !stepper.anchored
        while (position < text.length) {
            position = walk(text, state, position, skipping)
            state = walkState
            if (position >= text.length) break
            if (first != null && idle[state]) {
                val next = firstCandidate(text, position, first)
                if (next == text.length) return false
                if (next != position) {
                    position = next
                    val key = Key(intArrayOf(0), UnitContext.of(text[next - 1].code))
                    state = intern(key) ?: return stepper.matches(key, text, position, context)
                }
            }
            val next = step(state, text[position].code, position, context)
            if (accepts) return true
            overflow?.let { return stepper.matches(it, text, position + 1, context) }
            if (next == STOP) return false
            state = next
            position++
        }
        step(state, -1, position, context)
        return accepts
    }

    private fun walk(
        text: String,
        from: Int,
        origin: Int,
        skipping: Boolean,
    ): Int {
        var state = from
        var position = origin
        while (position < text.length && (!skipping || !idle[state])) {
            val next = table[state * width + classes.index(text[position])]
            if (next < 0) break
            state = next
            position++
        }
        walkState = state
        return position
    }

    private fun firstCandidate(
        text: String,
        from: Int,
        first: CharSet,
    ): Int {
        var next = from
        while (next < text.length && text[next] !in first) next++
        return next
    }

    fun acceptance(
        text: String,
        context: MatchContext,
    ): BooleanArray {
        mayReset = true
        val accepted = BooleanArray(text.length + 1)
        val backward = stepper.backward
        val last = text.length
        var state = 0
        for (offset in 0..last) {
            val position = if (backward) last - offset else offset
            val unit = if (offset == last) -1 else text[if (backward) position - 1 else position].code
            val slot = slot(state, unit)
            val target = table[slot]
            if (target >= 0) {
                accepted[position] = accepting[slot]
                state = target
                continue
            }
            val next = step(state, unit, position, context)
            accepted[position] = accepts
            overflow?.let {
                stepper.acceptance(it, text, offset + 1, accepted, context)
                return accepted
            }
            if (next == STOP) return accepted
            state = next
        }
        return accepted
    }

    fun reverse(
        text: String,
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
            val next = step(state, text.nextUnit(position, backward = true), position, context)
            if (accepts) return Verdict.MATCH
            val spilled = overflow
            if (spilled == null && next == STOP) return Verdict.MISS
            if (position == floor) return Verdict.UNDECIDED
            if (spilled != null) return stepper.run(spilled, text, position - 1, floor, context)
            state = next
            position--
        }
    }

    private fun step(
        state: Int,
        unit: Int,
        position: Int,
        context: MatchContext,
    ): Int {
        val slot = slot(state, unit)
        val target = table[slot]
        overflow = null
        if (target >= 0 || target == STOP) {
            accepts = accepting[slot]
            return target
        }
        if (target == VOLATILE) {
            memos[slot]?.takeIf { it.replays(context, position) }?.let {
                accepts = it.accepts
                return it.target
            }
        }
        return explore(state, slot, unit, position, context)
    }

    private fun slot(
        state: Int,
        unit: Int,
    ): Int = state * width + (if (unit < 0) classes.count else classes.index(unit.toChar()))

    private fun explore(
        state: Int,
        slot: Int,
        unit: Int,
        position: Int,
        context: MatchContext,
    ): Int {
        val key = keys[state]
        val advance = stepper.advance(key.pcs, key.context, unit, position, context)
        accepts = advance.accepts
        if (unit < 0 || advance.next.isEmpty() || (advance.accepts && !stepper.scanning)) {
            record(slot, STOP, advance)
            return STOP
        }
        val pcs = advance.next.toArray()
        pcs.sort()
        val next = Key(pcs, UnitContext.of(unit))
        val generation = resets
        val id = intern(next)
        if (id == null) {
            overflow = next
            return STOP
        }
        if (generation == resets) record(slot, id, advance)
        return id
    }

    private fun record(
        slot: Int,
        target: Int,
        advance: ThreadStep.Advance,
    ) {
        val looks = advance.looks
        table[slot] = if (looks == null) target else VOLATILE
        accepting[slot] = advance.accepts
        if (looks == null || memos[slot] != null) return
        val memo = LookMemo(looks, target, advance.accepts)
        if (memory.reserve(memo.bytes)) memos[slot] = memo
    }

    private fun intern(key: Key): Int? {
        lookup[key]?.let { return it }
        val cost = 4 * key.pcs.size + slotBytes * width + 96
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
            if (tracksLooks) memos = memos.copyOf(capacity * width)
        }
        keys.add(key)
        lookup[key] = state
        idle[state] = !stepper.anchored && key.pcs.size == 1 && key.pcs[0] == 0
        table.fill(UNKNOWN, state * width, (state + 1) * width)
        accepting.fill(false, state * width, (state + 1) * width)
        if (tracksLooks) memos.fill(null, state * width, (state + 1) * width)
        return state
    }

    companion object {
        val start: Key = Key(intArrayOf(0), UnitContext.EDGE)
        private const val UNKNOWN = -1
        private const val STOP = -2
        private const val VOLATILE = -3

        fun entry(
            text: String,
            end: Int,
        ): Key = Key(intArrayOf(0), if (end < text.length) UnitContext.of(text[end].code) else UnitContext.EDGE)
    }
}
