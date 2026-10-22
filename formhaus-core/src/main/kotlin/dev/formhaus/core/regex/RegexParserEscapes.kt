package dev.formhaus.core.regex

internal fun RegexParser.parseAtomEscape(): RegexParser.Atom {
    val unit = peek() ?: throw RegexSyntaxError
    when (unit) {
        'b', 'B' -> {
            index++
            val assertion = if (unit == 'b') RegexAssertion.WORD_BOUNDARY else RegexAssertion.NOT_WORD_BOUNDARY
            return RegexParser.Atom(RegexNode.Assertion(assertion), false)
        }
        in '1'..'9' -> decimalReference()?.let { return RegexParser.Atom(it, true) }
        'k' -> if (groups.hasNames) return RegexParser.Atom(namedReference(), true)
        'c' -> if (!controlFollows(inClass = false)) return literal('\\')
        else -> classEscape(unit)?.let {
            index++
            return setAtom(it)
        }
    }
    return literal(parseCharacterEscape())
}

private fun classEscape(unit: Char): CharSet? = when (unit) {
    'd' -> CharSet.digit
    'D' -> CharSet.notDigit
    's' -> CharSet.space
    'S' -> CharSet.notSpace
    'w' -> CharSet.word
    'W' -> CharSet.notWord
    else -> null
}

private fun RegexParser.controlFollows(inClass: Boolean): Boolean {
    val next = peek(1) ?: return false
    val letter = next in 'A'..'Z' || next in 'a'..'z'
    return letter || (inClass && (next in '0'..'9' || next == '_'))
}

private fun RegexParser.parseCharacterEscape(): Char {
    val unit = units[index]
    index++
    return when (unit) {
        'f' -> '\u000C'
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        'v' -> '\u000B'
        'c' -> {
            index++
            (units[index - 1].code % 32).toChar()
        }
        in '0'..'7' -> legacyOctal(unit)
        'x' -> hexEscape(2) ?: unit
        'u' -> hexEscape(4) ?: unit
        'k' -> if (groups.hasNames) throw RegexSyntaxError else unit
        else -> unit
    }
}

private fun RegexParser.hexEscape(count: Int): Char? {
    val value = hexValue(units, index, count) ?: return null
    index += count
    return value.toChar()
}

private fun RegexParser.legacyOctal(first: Char): Char {
    var value = first - '0'
    val second = octalDigit() ?: return value.toChar()
    value = value * 8 + second
    if (first <= '3') octalDigit()?.let { value = value * 8 + it }
    return value.toChar()
}

private fun RegexParser.octalDigit(): Int? {
    val unit = peek()?.takeIf { it in '0'..'7' } ?: return null
    index++
    return unit - '0'
}

private fun RegexParser.decimalReference(): RegexNode? {
    var cursor = index
    var value = 0L
    while (cursor < units.size && units[cursor] in '0'..'9') {
        value = minOf(value * 10 + (units[cursor] - '0'), Int.MAX_VALUE.toLong())
        cursor++
    }
    if (value > groups.names.size) return null
    index = cursor
    return RegexNode.Backreference(listOf(value.toInt()), modifiers.ignoreCase)
}

private fun RegexParser.namedReference(): RegexNode {
    index++
    if (!consume('<')) throw RegexSyntaxError
    val name = parseGroupName(units, index) ?: throw RegexSyntaxError
    index = name.end
    val indices = groups.indices(name.name)
    if (indices.isEmpty()) throw RegexSyntaxError
    return RegexNode.Backreference(indices, modifiers.ignoreCase)
}

internal fun RegexParser.parseClass(): RegexNode {
    val negated = consume('^')
    val ranges = IntList()
    while (!consume(']')) {
        if (peek() == null) throw RegexSyntaxError
        val first = parseClassAtom()
        val next = peek(1)
        if (!at('-') || next == null || next == ']') {
            first.addTo(ranges)
            continue
        }
        index++
        val second = parseClassAtom()
        val lower = first.single
        val upper = second.single
        if (lower != null && upper != null) {
            if (lower > upper) throw RegexSyntaxError
            ranges.addRange(lower.code, upper.code)
        } else {
            first.addTo(ranges)
            ranges.addRange('-'.code, '-'.code)
            second.addTo(ranges)
        }
    }
    val members = CharSet.of(ranges.toArray())
    val folded = if (modifiers.ignoreCase) CaseFolding.fold(members) else members
    return RegexNode.Set(if (negated) folded.inverted else folded)
}

private fun RegexParser.parseClassAtom(): ClassAtom {
    val unit = units[index]
    index++
    if (unit != '\\') return ClassAtom(unit, null)
    val next = peek() ?: throw RegexSyntaxError
    if (next == 'b') {
        index++
        return ClassAtom('\b', null)
    }
    if (next == 'c' && !controlFollows(inClass = true)) return ClassAtom(unit, null)
    classEscape(next)?.let {
        index++
        return ClassAtom(null, it)
    }
    return ClassAtom(parseCharacterEscape(), null)
}

private class ClassAtom(val single: Char?, private val set: CharSet?) {
    fun addTo(ranges: IntList) {
        if (single != null) ranges.addRange(single.code, single.code)
        if (set != null) for (index in 0 until set.rangeCount) ranges.addRange(set.lowerAt(index), set.upperAt(index))
    }
}
