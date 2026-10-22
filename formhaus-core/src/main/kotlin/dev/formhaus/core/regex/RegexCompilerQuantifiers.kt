package dev.formhaus.core.regex

import dev.formhaus.core.validation.MAX_STRING_LENGTH

internal fun RegexCompiler.emitQuantified(
    body: RegexNode,
    requested: Quantifier,
    backward: Boolean,
) {
    if (requested.max == 0) return
    val quantifier = clamp(requested, body)
    if (!automaton) {
        singleSet(body)?.let { set ->
            code.add(Instruction.RepeatSet(set, quantifier.min, quantifier.max, quantifier.greedy, backward))
            return
        }
    }
    val mark = if (body.canBeEmpty && !automaton) newRegister() else null
    var count = 0
    while (count < quantifier.min && !oversized) {
        emitIteration(body, null, backward)
        count++
    }
    val max = quantifier.max
    if (max != null) {
        emitOptional(body, max - quantifier.min, quantifier.greedy, mark, backward)
    } else {
        emitStar(body, quantifier.greedy, mark, backward)
    }
}

private fun RegexCompiler.clamp(
    quantifier: Quantifier,
    body: RegexNode,
): Quantifier {
    val length = body.minimumLength
    val usable =
        if (length ==
            0
        ) {
            MAX_STRING_LENGTH + 1
        } else {
            ((MAX_STRING_LENGTH.toLong() + length - 1) / length).toInt()
        }
    val min = minOf(quantifier.min, usable + 1)
    val max = quantifier.max?.let { maxOf(minOf(it, usable), min) }
    if (min != quantifier.min || max != quantifier.max) clamped = true
    return Quantifier(min, max, quantifier.greedy)
}

private fun RegexCompiler.singleSet(body: RegexNode): Int? =
    when (body) {
        is RegexNode.Unit -> intern(CharSet.unit(body.unit))
        is RegexNode.Set -> intern(body.set)
        else -> null
    }

private fun RegexCompiler.emitIteration(
    body: RegexNode,
    mark: Int?,
    backward: Boolean,
) {
    if (!automaton) body.captureRange?.let { code.add(Instruction.State(StateChange.Clear(it))) }
    if (mark != null) code.add(Instruction.State(StateChange.Mark(mark)))
    emit(body, backward)
    if (mark != null) code.add(Instruction.State(StateChange.Progress(mark)))
}

private fun RegexCompiler.emitStar(
    body: RegexNode,
    greedy: Boolean,
    mark: Int?,
    backward: Boolean,
) {
    val loop = code.size
    code.add(Instruction.Succeed)
    emitIteration(body, mark, backward)
    code.add(Instruction.Jump(loop))
    code[loop] = if (greedy) Instruction.Split(loop + 1, code.size) else Instruction.Split(code.size, loop + 1)
    if (inMain && dependentEnd > loop) dependentEnd = code.size
}

private fun RegexCompiler.emitOptional(
    body: RegexNode,
    count: Int,
    greedy: Boolean,
    mark: Int?,
    backward: Boolean,
) {
    val splits = ArrayList<Int>()
    while (splits.size < count && !oversized) {
        splits.add(code.size)
        code.add(Instruction.Succeed)
        emitIteration(body, mark, backward)
    }
    val end = code.size
    for (split in splits) {
        code[split] =
            if (greedy) Instruction.Split(split + 1, end) else Instruction.Split(end, split + 1)
    }
}
