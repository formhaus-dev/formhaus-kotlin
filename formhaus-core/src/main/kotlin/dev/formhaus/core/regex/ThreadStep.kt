package dev.formhaus.core.regex

internal enum class Verdict { MATCH, MISS, UNDECIDED }

internal class ThreadStep(
    val program: RegexProgram,
    val anchored: Boolean,
    val backward: Boolean,
    val scanning: Boolean,
) {
    class Advance(
        val next: IntList,
        val accepts: Boolean,
        val looks: IntArray?,
    )

    private var seen = IntArray(program.instructions.size)
    private var generation = 0
    private val stack = IntList()
    private val consumers = IntList()
    private var accepts = false
    private val looks = IntList()

    fun advance(
        pcs: IntArray,
        previous: UnitContext,
        unit: Int,
        position: Int,
        context: MatchContext,
    ): Advance {
        val current = UnitContext.of(unit)
        closure(
            pcs,
            if (backward) current else previous,
            if (backward) previous else current,
            position,
            context,
        )
        val consulted = if (looks.isEmpty()) null else looks.toArray()
        val next = IntList(consumers.size + 1)
        if ((accepts && !scanning) || unit < 0) return Advance(next, accepts, consulted)
        val char = unit.toChar()
        for (index in 0 until consumers.size) {
            val pc = consumers[index]
            if (program.consumes(pc, char)) next.add(pc + 1)
        }
        if (!anchored) next.add(0)
        return Advance(next, accepts, consulted)
    }

    private fun closure(
        pcs: IntArray,
        before: UnitContext,
        after: UnitContext,
        position: Int,
        context: MatchContext,
    ) {
        generation++
        if (generation == 0) {
            seen = IntArray(seen.size)
            generation = 1
        }
        accepts = false
        looks.truncate(0)
        consumers.truncate(0)
        stack.truncate(0)
        for (index in pcs.indices.reversed()) stack.add(pcs[index])
        var visited = 0
        while (!stack.isEmpty()) {
            val pc = stack.pop()
            if (seen[pc] == generation) continue
            seen[pc] = generation
            visited++
            expand(pc, before, after, position, context)
        }
        context.spend(visited)
    }

    private fun expand(
        pc: Int,
        before: UnitContext,
        after: UnitContext,
        position: Int,
        context: MatchContext,
    ) {
        when (val instruction = program.instructions[pc]) {
            is Instruction.Unit, is Instruction.Set -> {
                consumers.add(pc)
            }

            is Instruction.Split -> {
                stack.add(instruction.second)
                stack.add(instruction.first)
            }

            is Instruction.Jump -> {
                stack.add(instruction.target)
            }

            is Instruction.Assertion -> {
                if (instruction.assertion.holds(before, after)) stack.add(pc + 1)
            }

            is Instruction.Look -> {
                val holds = context.holds(instruction.look, position)
                looks.add(LookOutcome.of(instruction.look, holds))
                if (holds != program.looks[instruction.look].look.negated) stack.add(pc + 1)
            }

            Instruction.Match, Instruction.Succeed -> {
                accepts = true
            }

            is Instruction.State -> {
                stack.add(pc + 1)
            }

            is Instruction.RepeatSet, is Instruction.Backreference -> {
                error("automaton programs have no stateful instructions")
            }
        }
    }

    fun matches(
        start: LazyDFA.Key,
        text: String,
        offset: Int,
        context: MatchContext,
    ): Boolean = run(start, text, offset, text.length, context) == Verdict.MATCH

    fun run(
        start: LazyDFA.Key,
        text: String,
        origin: Int,
        floor: Int,
        context: MatchContext,
    ): Verdict {
        var pcs = start.pcs
        var previous = start.context
        var position = origin
        while (true) {
            val unit = text.nextUnit(position, backward)
            val step = advance(pcs, previous, unit, position, context)
            if (step.accepts) return Verdict.MATCH
            if (unit < 0 || step.next.isEmpty()) return Verdict.MISS
            if (position == floor) return Verdict.UNDECIDED
            pcs = step.next.toArray()
            previous = UnitContext.of(unit)
            position += if (backward) -1 else 1
        }
    }

    fun acceptance(
        start: LazyDFA.Key,
        text: String,
        offset: Int,
        accepted: BooleanArray,
        context: MatchContext,
    ) {
        var pcs = start.pcs
        var previous = start.context
        for (step in offset..text.length) {
            val position = if (backward) text.length - step else step
            val unit = text.nextUnit(position, backward)
            val result = advance(pcs, previous, unit, position, context)
            accepted[position] = result.accepts
            if (unit < 0 || result.next.isEmpty()) return
            pcs = result.next.toArray()
            previous = UnitContext.of(unit)
        }
    }
}
