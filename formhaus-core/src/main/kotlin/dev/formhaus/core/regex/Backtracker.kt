package dev.formhaus.core.regex

internal class Backtracker(
    val program: RegexProgram,
    val context: MatchContext,
) {
    val text: CharArray = context.text
    var slots: IntArray = IntArray(2 * (program.captureCount + 1)) { -1 }
    val registers: IntArray = IntArray(program.registerCount) { -1 }
    val frames: Frames = Frames()
    val saved: ArrayList<IntArray> = ArrayList()
    private val width = text.size + 1
    private val memo: Bitset?
    var pc: Int = 0
    var offset: Int = 0

    init {
        val region = program.mainEnd - program.memoFrom
        memo = if (region > 0 && region.toLong() * width <= MEMO_LIMIT) Bitset(region * width) else null
    }

    fun search(prefilter: Prefilter): Boolean {
        for (start in 0..(text.size - prefilter.minimumLength)) {
            if (prefilter.anchored && start > 0) return false
            val first = prefilter.first
            if (first != null && (start == text.size || text[start] !in first)) continue
            if (run(0, start)) return true
        }
        return false
    }

    fun run(
        entry: Int,
        position: Int,
    ): Boolean {
        val outerPc = pc
        val outerOffset = offset
        val base = frames.size
        pc = entry
        offset = position
        try {
            while (true) {
                context.spend(1)
                when (execute()) {
                    PROCEED -> continue
                    SUCCESS -> return true
                    else -> if (!backtrack(base)) return false
                }
            }
        } finally {
            pc = outerPc
            offset = outerOffset
        }
    }

    private fun backtrack(base: Int): Boolean {
        while (frames.size > base) {
            val frame = frames.pop()
            when (frames.kind(frame)) {
                Frames.RESUME -> {
                    pc = frames.a(frame)
                    offset = frames.b(frame)
                    return true
                }

                Frames.REGISTER -> {
                    registers[frames.a(frame)] = frames.b(frame)
                }

                Frames.CAPTURE -> {
                    val group = frames.a(frame)
                    slots[2 * group] = frames.b(frame)
                    slots[2 * group + 1] = frames.c(frame)
                }

                Frames.CAPTURES -> {
                    val index = frames.a(frame)
                    slots = saved[index]
                    saved.subList(index, saved.size).clear()
                }

                Frames.SCAN -> {
                    resumeScan(frames.scan(frame))
                    return true
                }

                else -> {
                    if (resumeExtend(frames.scan(frame))) return true
                }
            }
        }
        return false
    }

    fun memoized(pc: Int): Boolean = memo != null && pc >= program.memoFrom && pc < program.mainEnd

    private fun execute(): Int {
        if (memoized(pc) && memo?.insert((pc - program.memoFrom) * width + offset) == false) return FAILURE
        return when (val instruction = program.instructions[pc]) {
            is Instruction.Unit, is Instruction.Set -> {
                if (consume()) PROCEED else FAILURE
            }

            is Instruction.Split -> {
                split(instruction)
            }

            is Instruction.Jump -> {
                pc = instruction.target
                PROCEED
            }

            Instruction.Succeed, Instruction.Match -> {
                SUCCESS
            }

            else -> {
                if (passes(instruction)) {
                    pc++
                    PROCEED
                } else {
                    FAILURE
                }
            }
        }
    }

    private fun split(instruction: Instruction.Split): Int {
        if (program.instructions[instruction.second] == Instruction.Match) return SUCCESS
        frames.push(Frames.RESUME, instruction.second, offset)
        pc = instruction.first
        return PROCEED
    }

    private fun passes(instruction: Instruction): Boolean =
        when (instruction) {
            is Instruction.Assertion -> text.holds(instruction.assertion, offset)
            is Instruction.Look -> evaluateLook(instruction.look, offset)
            is Instruction.Backreference -> matchReference(program.references[instruction.reference], offset)?.also { offset = it } != null
            is Instruction.RepeatSet -> enterRepeat(instruction, pc, offset)?.also { offset = it } != null
            is Instruction.State -> apply(instruction.change, pc, offset)
            else -> true
        }

    private fun consume(): Boolean {
        var steps = 0
        var matched = true
        while (steps == 0 || memo == null) {
            val instruction = program.instructions[pc]
            val backward =
                when (instruction) {
                    is Instruction.Unit -> {
                        matched = text.nextUnit(offset, instruction.backward) == instruction.unit.code
                        instruction.backward
                    }

                    is Instruction.Set -> {
                        matched =
                            program.sets[instruction.set].containsCode(text.nextUnit(offset, instruction.backward))
                        instruction.backward
                    }

                    else -> {
                        break
                    }
                }
            if (!matched) break
            offset += if (backward) -1 else 1
            pc++
            steps++
        }
        context.spend(maxOf(steps - 1, 0))
        return matched
    }

    private companion object {
        const val MEMO_LIMIT = 1 shl 25
        const val PROCEED = 0
        const val SUCCESS = 1
        const val FAILURE = 2
    }
}

internal class Bitset(
    count: Int,
) {
    private val words = LongArray((count + 63) / 64)

    fun insert(index: Int): Boolean {
        val bit = 1L shl (index and 63)
        val old = words[index shr 6]
        words[index shr 6] = old or bit
        return old and bit == 0L
    }
}
