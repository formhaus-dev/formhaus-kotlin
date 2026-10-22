package dev.formhaus.core.regex

internal sealed interface Instruction {
    data class Unit(
        val unit: Char,
        val backward: Boolean,
    ) : Instruction

    data class Set(
        val set: Int,
        val backward: Boolean,
    ) : Instruction

    data class Split(
        val first: Int,
        val second: Int,
    ) : Instruction

    data class Jump(
        val target: Int,
    ) : Instruction

    data class Assertion(
        val assertion: RegexAssertion,
    ) : Instruction

    data class State(
        val change: StateChange,
    ) : Instruction

    data class RepeatSet(
        val set: Int,
        val min: Int,
        val max: Int?,
        val greedy: Boolean,
        val backward: Boolean,
    ) : Instruction

    data class Backreference(
        val reference: Int,
    ) : Instruction

    data class Look(
        val look: Int,
    ) : Instruction

    data object Succeed : Instruction

    data object Match : Instruction
}

internal sealed interface StateChange {
    data class GroupStart(
        val group: Int,
    ) : StateChange

    data class GroupEnd(
        val group: Int,
    ) : StateChange

    data class Clear(
        val groups: IntRange,
    ) : StateChange

    data class Mark(
        val register: Int,
    ) : StateChange

    data class Progress(
        val register: Int,
    ) : StateChange
}

internal class Backreference(
    val groups: List<Int>,
    val backward: Boolean,
    val ignoreCase: Boolean,
)

internal class LookBody(
    val look: Look,
    val start: Int?,
)

internal class RegexProgram(
    val instructions: List<Instruction>,
    val sets: List<CharSet>,
    val looks: List<LookBody>,
    val scans: List<RegexProgram?>,
    val size: Int,
    val clamped: Boolean,
    val references: List<Backreference>,
    val captureCount: Int,
    val registerCount: Int,
    val mainEnd: Int,
    val memoFrom: Int,
    val automaton: Boolean,
) {
    fun consumes(
        pc: Int,
        unit: Char,
    ): Boolean =
        when (val instruction = instructions[pc]) {
            is Instruction.Unit -> instruction.unit == unit
            is Instruction.Set -> unit in sets[instruction.set]
            else -> false
        }
}

internal enum class UnitContext {
    EDGE,
    WORD,
    LINE_TERMINATOR,
    OTHER,
    ;

    companion object {
        fun of(code: Int): UnitContext =
            when {
                code < 0 -> EDGE
                CharSet.word.containsCode(code) -> WORD
                CharSet.lineTerminator.containsCode(code) -> LINE_TERMINATOR
                else -> OTHER
            }
    }
}

internal fun RegexAssertion.holds(
    before: UnitContext,
    after: UnitContext,
): Boolean =
    when (this) {
        RegexAssertion.INPUT_START -> before == UnitContext.EDGE
        RegexAssertion.INPUT_END -> after == UnitContext.EDGE
        RegexAssertion.LINE_START -> before == UnitContext.EDGE || before == UnitContext.LINE_TERMINATOR
        RegexAssertion.LINE_END -> after == UnitContext.EDGE || after == UnitContext.LINE_TERMINATOR
        RegexAssertion.WORD_BOUNDARY -> (before == UnitContext.WORD) != (after == UnitContext.WORD)
        RegexAssertion.NOT_WORD_BOUNDARY -> (before == UnitContext.WORD) == (after == UnitContext.WORD)
    }

internal fun CharArray.nextUnit(
    position: Int,
    backward: Boolean,
): Int =
    when {
        backward -> if (position > 0) this[position - 1].code else -1
        else -> if (position < size) this[position].code else -1
    }

internal fun CharArray.holds(
    assertion: RegexAssertion,
    position: Int,
): Boolean {
    if (assertion == RegexAssertion.INPUT_START) return position == 0
    if (assertion == RegexAssertion.INPUT_END) return position == size
    return assertion.holds(
        UnitContext.of(nextUnit(position, backward = true)),
        UnitContext.of(nextUnit(position, backward = false)),
    )
}
