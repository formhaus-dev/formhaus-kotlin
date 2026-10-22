package dev.formhaus.core.regex

internal fun RegexParser.parseAtom(): RegexParser.Atom {
    val unit = units[index]
    index++
    return when (unit) {
        '^' -> RegexParser.Atom(
            RegexNode.Assertion(if (modifiers.multiline) RegexAssertion.LINE_START else RegexAssertion.INPUT_START), false,
        )
        '$' -> RegexParser.Atom(
            RegexNode.Assertion(if (modifiers.multiline) RegexAssertion.LINE_END else RegexAssertion.INPUT_END), false,
        )
        '.' -> RegexParser.Atom(RegexNode.Set(if (modifiers.dotAll) CharSet.all else CharSet.dot), true)
        '(' -> parseGroup()
        '[' -> RegexParser.Atom(parseClass(), true)
        '\\' -> parseAtomEscape()
        '*', '+', '?' -> throw RegexSyntaxError
        '{' -> if (isBracedQuantifier(index - 1)) throw RegexSyntaxError else literal(unit)
        else -> literal(unit)
    }
}

internal fun RegexParser.literal(unit: Char): RegexParser.Atom {
    if (!modifiers.ignoreCase) return RegexParser.Atom(RegexNode.Unit(unit), true)
    val folded = CaseFolding.fold(CharSet.unit(unit))
    return RegexParser.Atom(folded.singleUnit?.let(RegexNode::Unit) ?: RegexNode.Set(folded), true)
}

internal fun RegexParser.setAtom(set: CharSet): RegexParser.Atom =
    RegexParser.Atom(RegexNode.Set(if (modifiers.ignoreCase) CaseFolding.fold(set) else set), true)

private fun RegexParser.parseGroup(): RegexParser.Atom {
    if (!consume('?')) return parseCapture(null)
    if (consume(':')) return RegexParser.Atom(RegexNode.Group(parseGroupBody(), null), true)
    if (at('=') || at('!')) {
        val look = Look(behind = false, negated = at('!'), index = looks)
        looks++
        index++
        return RegexParser.Atom(RegexNode.Lookaround(parseGroupBody(), look), true)
    }
    if (at('<') && (at('=', 1) || at('!', 1))) {
        val look = Look(behind = true, negated = at('!', 1), index = looks)
        looks++
        index += 2
        return RegexParser.Atom(RegexNode.Lookaround(parseGroupBody(), look), false)
    }
    if (at('<')) {
        val name = parseGroupName(units, index + 1) ?: throw RegexSyntaxError
        index = name.end
        return parseCapture(name.name)
    }
    return RegexParser.Atom(parseModified(), true)
}

private fun RegexParser.parseGroupBody(capture: Int? = null, name: String? = null): RegexNode {
    val enclosing = scope
    try {
        scope = enclosing.opening(capture ?: captures)
        val body = parseDisjunction()
        if (!consume(')')) throw RegexSyntaxError
        if (name != null && capture != null) {
            if (named.any { it.name == name && !scope.admitsDuplicate(it.index) }) throw RegexSyntaxError
            named.add(RegexParser.NamedGroup(name, capture))
        }
        return body
    } finally {
        scope = enclosing
    }
}

private fun RegexParser.parseCapture(name: String?): RegexParser.Atom {
    captures++
    val capture = captures
    return RegexParser.Atom(RegexNode.Group(parseGroupBody(capture, name), capture), true)
}

private fun RegexParser.parseModified(): RegexNode {
    val added = modifierFlags()
    val removed = if (consume('-')) modifierFlags() else null
    val flags = added + removed.orEmpty()
    if (!consume(':') || flags.toSet().size != flags.size || (removed != null && flags.isEmpty())) throw RegexSyntaxError
    val saved = modifiers
    try {
        for (flag in added) modifiers = modified(flag, true)
        for (flag in removed.orEmpty()) modifiers = modified(flag, false)
        return RegexNode.Group(parseGroupBody(), null)
    } finally {
        modifiers = saved
    }
}

private fun RegexParser.modifierFlags(): List<Char> {
    val flags = ArrayList<Char>()
    while (true) {
        val unit = peek() ?: break
        if (unit != 'i' && unit != 'm' && unit != 's') break
        flags.add(unit)
        index++
    }
    return flags
}

private fun RegexParser.modified(flag: Char, value: Boolean): RegexModifiers = when (flag) {
    'i' -> modifiers.copy(ignoreCase = value)
    'm' -> modifiers.copy(multiline = value)
    else -> modifiers.copy(dotAll = value)
}
