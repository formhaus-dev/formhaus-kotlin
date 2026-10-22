package dev.formhaus.core.regex

private sealed interface Gate {
    data object Open : Gate

    class Unit(
        val expected: Int,
        val backward: Boolean,
    ) : Gate

    class Members(
        val set: CharSet,
        val backward: Boolean,
    ) : Gate

    class Folded(
        val expected: Char,
        val backward: Boolean,
    ) : Gate
}

internal fun Backtracker.enterRepeat(
    repeated: Instruction.RepeatSet,
    pc: Int,
    offset: Int,
): Int? {
    val step = if (repeated.backward) -1 else 1
    val limit = if (repeated.greedy) repeated.max ?: Int.MAX_VALUE else repeated.min
    val members = Gate.Members(program.sets[repeated.set], repeated.backward)
    var position = offset
    var count = 0
    while (count < limit && admits(members, position)) {
        position += step
        count++
    }
    context.spend(count)
    if (count < repeated.min) return null
    if (repeated.greedy) {
        if (count > repeated.min) {
            frames.push(Frames.SCAN, Frames.Scan(pc + 1, position, offset + step * repeated.min, -step))
        }
    } else if (repeated.max?.let { it > repeated.min } != false) {
        val stop = repeated.max?.let { offset + step * it } ?: if (repeated.backward) -1 else text.size + 1
        frames.push(Frames.EXTEND, Frames.Scan(pc, position, stop, step))
    }
    return position
}

internal fun Backtracker.resumeScan(scan: Frames.Scan) {
    val next = gate(scan.pc)
    var position = scan.position + scan.step
    var skipped = 0
    while (position != scan.stop && !admits(next, position)) {
        position += scan.step
        skipped++
    }
    context.spend(skipped)
    if (position != scan.stop) frames.push(Frames.SCAN, Frames.Scan(scan.pc, position, scan.stop, scan.step))
    pc = scan.pc
    offset = position
}

internal fun Backtracker.resumeExtend(scan: Frames.Scan): Boolean {
    val repeated = program.instructions[scan.pc] as? Instruction.RepeatSet ?: return false
    val members = Gate.Members(program.sets[repeated.set], repeated.backward)
    val next = gate(scan.pc + 1)
    var position = scan.position
    var steps = 0
    do {
        if (!admits(members, position)) {
            context.spend(steps)
            return false
        }
        position += scan.step
        steps++
    } while (position != scan.stop && !admits(next, position))
    context.spend(steps)
    if (position != scan.stop) frames.push(Frames.EXTEND, Frames.Scan(scan.pc, position, scan.stop, scan.step))
    pc = scan.pc + 1
    offset = position
    return true
}

private fun Backtracker.gate(entry: Int): Gate {
    var pc = entry
    while (true) {
        when (val instruction = program.instructions[pc]) {
            is Instruction.Unit -> return Gate.Unit(instruction.unit.code, instruction.backward)

            is Instruction.Set -> return Gate.Members(program.sets[instruction.set], instruction.backward)

            is Instruction.Backreference -> return if (pc ==
                entry
            ) {
                gate(program.references[instruction.reference])
            } else {
                Gate.Open
            }

            is Instruction.State -> if (instruction.change is StateChange.Progress) return Gate.Open else pc++

            is Instruction.Jump -> pc = instruction.target

            else -> return Gate.Open
        }
    }
}

private fun Backtracker.gate(reference: Backreference): Gate {
    val group = reference.groups.singleOrNull() ?: return Gate.Open
    val start = slots[2 * group]
    val end = slots[2 * group + 1]
    if (start < 0 || end <= start) return Gate.Open
    val expected = text[if (reference.backward) end - 1 else start]
    if (!reference.ignoreCase) return Gate.Unit(expected.code, reference.backward)
    return Gate.Folded(CaseFolding.canonical[expected.code], reference.backward)
}

private fun Backtracker.admits(
    gate: Gate,
    position: Int,
): Boolean =
    when (gate) {
        Gate.Open -> {
            true
        }

        is Gate.Unit -> {
            text.nextUnit(position, gate.backward) == gate.expected
        }

        is Gate.Members -> {
            gate.set.containsCode(text.nextUnit(position, gate.backward))
        }

        is Gate.Folded -> {
            text
                .nextUnit(
                    position,
                    gate.backward,
                ).let { it >= 0 && CaseFolding.canonical[it] == gate.expected }
        }
    }
