package dev.formhaus.core.definitionvalidation

internal enum class SlowPattern { NESTED, ADJACENT, LONG }

internal const val SLOW_PATTERN_LENGTH_LIMIT = 1000

private class PatternGroup(
    val look: Boolean = false,
    var inner: Boolean = false,
    var alternated: Boolean = false,
    val entry: AsciiSet = AsciiSet(),
    var previous: AsciiSet = entry,
) {
    var chars = AsciiCounts()
    private var hasLead = false
    var lead: Char? = null
        private set
    var tail: Char? = null
        private set

    val separated: Char?
        get() = if (alternated) null else listOfNotNull(lead, tail).firstOrNull(chars::appearsOnce)

    fun separate(alternation: Boolean) {
        inner = inner || alternation
        alternated = alternated || alternation
        previous = if (alternation) entry else AsciiSet()
    }

    fun append(
        added: AsciiCounts,
        separator: Char?,
    ) {
        chars += added
        if (!hasLead) {
            hasLead = true
            lead = separator
        }
        tail = separator
    }
}

private class Repeated(
    val own: AsciiCounts,
    val first: AsciiSet,
    val last: AsciiSet,
) {
    constructor(set: AsciiSet) : this(AsciiCounts() + set, set, set)
}

private class Sampler {
    private val cache = HashMap<String, AsciiSet>()

    fun samples(atom: String): AsciiSet = cache.getOrPut(atom) { sample(atom) }

    private fun sample(atom: String): AsciiSet {
        if (atom.length > 1 && atom[0] == '\\' && (atom[1] == 'k' || atom[1] in '1'..'9')) return AsciiSet.ALL
        return asciiMatches("^(?:$atom)$")
    }
}

private fun isSeparatorAtom(atom: String): Boolean =
    when (atom.length) {
        1 -> !atom[0].isAsciiWord()
        2 -> atom[0] == '\\' && !atom[1].isAsciiWord()
        else -> false
    }

private sealed interface Trailing {
    data object Adjacent : Trailing

    class Previous(
        val chars: AsciiSet,
    ) : Trailing
}

private fun trailing(
    previous: AsciiSet,
    quantifier: PatternQuantifier,
    repeated: Repeated,
): Trailing {
    if (!quantifier.unbounded) return Trailing.Previous(if (quantifier.optional) previous else AsciiSet())
    if (previous.intersects(repeated.first)) return Trailing.Adjacent
    return Trailing.Previous(if (quantifier.optional) previous union repeated.last else repeated.last)
}

private sealed interface Closing {
    data object Invalid : Closing

    data object Nested : Closing

    class Closed(
        val group: PatternGroup,
        val repeated: Repeated,
    ) : Closing
}

private fun close(
    groups: MutableList<PatternGroup>,
    quantifier: PatternQuantifier,
): Closing {
    if (groups.size < 2) return Closing.Invalid
    val closed = groups.removeAt(groups.size - 1)
    val separated = closed.separated
    if (closed.inner && separated == null && quantifier.repeats) return Closing.Nested
    val all = closed.chars.once
    val first = if (separated != null && separated == closed.lead) AsciiSet().with(separated) else all
    val last = if (separated != null && separated == closed.tail) AsciiSet().with(separated) else all
    val parent = groups.last()
    parent.inner = parent.inner || closed.inner
    return Closing.Closed(closed, Repeated(closed.chars, first, last))
}

private sealed interface ScanStep {
    data object Next : ScanStep

    class Done(
        val result: SlowPattern?,
    ) : ScanStep
}

private class SlowPatternScan {
    private val sampler = Sampler()
    private val groups = mutableListOf(PatternGroup())

    fun open(look: Boolean) {
        val entry = if (look) AsciiSet() else groups.last().previous
        groups.add(PatternGroup(look = look, entry = entry))
    }

    fun separate(alternation: Boolean) = groups.last().separate(alternation)

    fun atom(
        atom: String,
        quantifier: PatternQuantifier,
    ): ScanStep {
        val repeated: Repeated
        var closed: PatternGroup? = null
        var separator: Char? = null
        if (atom == ")") {
            when (val closing = close(groups, quantifier)) {
                Closing.Invalid -> {
                    return ScanStep.Done(null)
                }

                Closing.Nested -> {
                    return ScanStep.Done(SlowPattern.NESTED)
                }

                is Closing.Closed -> {
                    closed = closing.group
                    repeated = closing.repeated
                }
            }
        } else {
            val own = sampler.samples(atom)
            repeated = Repeated(own)
            if (!quantifier.present && isSeparatorAtom(atom)) separator = own.single
        }
        val current = groups.last()
        current.append(repeated.own, separator)
        if (closed != null && (closed.look || !quantifier.present)) {
            current.previous = if (closed.look || closed.alternated) AsciiSet() else closed.previous
            return ScanStep.Next
        }
        current.inner = current.inner || quantifier.present
        return when (val next = trailing(current.previous, quantifier, repeated)) {
            Trailing.Adjacent -> {
                ScanStep.Done(SlowPattern.ADJACENT)
            }

            is Trailing.Previous -> {
                current.previous = next.chars
                ScanStep.Next
            }
        }
    }
}

internal fun slowPattern(pattern: String): SlowPattern? {
    if (pattern.length > SLOW_PATTERN_LENGTH_LIMIT) return SlowPattern.LONG
    val lexer = PatternLexer(pattern.toCharArray())
    val units = lexer.units
    val scan = SlowPatternScan()
    var index = 0
    while (index < units.size) {
        val token = lexer.token(index)
        when (val kind = token.kind) {
            is TokenKind.Open -> {
                scan.open(kind.look)
                index = token.end
            }

            TokenKind.Other -> {
                scan.separate(units[index] == '|')
                index = token.end
            }

            is TokenKind.Atom -> {
                val quantifier = lexer.quantifier(kind.end)
                val step = scan.atom(String(units, index, kind.end - index), quantifier)
                if (step is ScanStep.Done) return step.result
                index = quantifier.end
            }
        }
    }
    return null
}
