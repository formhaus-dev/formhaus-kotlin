package dev.formhaus.core.regex

private fun Backtracker.setRegister(index: Int, value: Int) {
    if (registers[index] == value) return
    frames.push(Frames.REGISTER, index, registers[index])
    registers[index] = value
}

private fun Backtracker.setCapture(group: Int, start: Int, end: Int) {
    if (slots[2 * group] == start && slots[2 * group + 1] == end) return
    frames.push(Frames.CAPTURE, group, slots[2 * group], slots[2 * group + 1])
    slots[2 * group] = start
    slots[2 * group + 1] = end
}

internal fun Backtracker.apply(change: StateChange, pc: Int, offset: Int): Boolean {
    when (change) {
        is StateChange.GroupStart -> setRegister(change.group, offset)
        is StateChange.Mark -> setRegister(change.register, offset)
        is StateChange.GroupEnd ->
            setCapture(change.group, minOf(registers[change.group], offset), maxOf(registers[change.group], offset))
        is StateChange.Clear -> {
            context.spend(change.groups.last - change.groups.first + 1)
            for (group in change.groups) setCapture(group, -1, -1)
        }
        is StateChange.Progress -> return memoized(pc) || registers[change.register] != offset
    }
    return true
}

internal fun Backtracker.matchReference(reference: Backreference, offset: Int): Int? {
    var position = offset
    context.spend(reference.groups.size)
    for (group in if (reference.backward) reference.groups else reference.groups.asReversed()) {
        if (slots[2 * group] < 0) continue
        context.spend(slots[2 * group + 1] - slots[2 * group] + 1)
        position = matchCapture(group, reference, position) ?: return null
    }
    return position
}

private fun Backtracker.matchCapture(group: Int, reference: Backreference, offset: Int): Int? {
    val start = slots[2 * group]
    val length = slots[2 * group + 1] - start
    val from = if (reference.backward) offset - length else offset
    if (from < 0 || from + length > text.size) return null
    for (index in 0 until length) {
        val left = text[start + index]
        val right = text[from + index]
        if (left != right && !(reference.ignoreCase && CaseFolding.canonical[left.code] == CaseFolding.canonical[right.code])) {
            return null
        }
    }
    return if (reference.backward) from else offset + length
}

internal fun Backtracker.evaluateLook(index: Int, offset: Int): Boolean {
    val body = program.looks[index]
    val start = body.start ?: return context.holds(index, offset) != body.look.negated
    context.spend(2 * slots.size)
    val snapshot = slots.copyOf()
    if (!body.look.negated) {
        saved.add(snapshot)
        frames.push(Frames.CAPTURES, saved.size - 1)
    }
    val base = frames.size
    val savedBase = saved.size
    val found = run(start, offset)
    if (found) {
        frames.truncate(base)
        saved.subList(savedBase, saved.size).clear()
        if (body.look.negated) slots = snapshot
    } else if (!body.look.negated) {
        frames.dropLast()
        saved.removeAt(saved.size - 1)
    }
    return found != body.look.negated
}
