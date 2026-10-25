package dev.formhaus.core.definitionvalidation

internal enum class SlowPattern { NESTED, ADJACENT, LONG }

private const val SLOW_PATTERN_LENGTH_LIMIT = 1000
private const val MAX_BOUNDED_REPEAT = 10

private class Part(
    val chars: PatternChars,
    val quantifier: PatternQuantifier,
    val atom: Boolean,
)

private class Repeated(
    val chars: PatternChars,
    val first: PatternChars = chars,
    val last: PatternChars = chars,
)

private class PatternGroup(
    val look: Boolean = false,
    val entry: PatternChars = PatternChars.empty,
) {
    var previous: PatternChars = entry
    var inner: Boolean = false
    var unbounded: Boolean = false
    var alternated: Boolean = false
    val parts: MutableList<Part> = ArrayList()

    fun separate(alternation: Boolean) {
        inner = inner || alternation
        alternated = alternated || alternation
        previous = if (alternation) entry else PatternChars.empty
    }

    fun repeats(quantifier: PatternQuantifier): Boolean {
        if (!inner || !quantifier.repeats || quantifier.exact) return false
        return quantifier.unbounded || unbounded || quantifier.upper > MAX_BOUNDED_REPEAT
    }

    private fun separator(index: Int): Part? {
        val part = parts.getOrNull(index)?.takeIf { it.atom && it.quantifier.mandatory } ?: return null
        val others = parts.filterIndexed { at, _ -> at != index }
        if (part.chars overlaps PatternChars.union(others.map { it.chars })) return null
        return part.takeIf { it.quantifier.exact || others.any { other -> other.quantifier.mandatory } }
    }

    fun separated(chars: PatternChars): Repeated? {
        if (alternated) return null
        val lead = separator(0)
        val tail = separator(parts.size - 1)
        return when {
            lead != null && (lead.quantifier.exact || tail?.quantifier?.exact != true) -> Repeated(chars, first = lead.chars)
            tail != null -> Repeated(chars, last = tail.chars)
            else -> null
        }
    }
}

private sealed interface Trailing {
    data object Adjacent : Trailing

    class Previous(
        val chars: PatternChars,
    ) : Trailing
}

private fun trailing(
    previous: PatternChars,
    quantifier: PatternQuantifier,
    repeated: Repeated,
): Trailing {
    if (!quantifier.unbounded) return Trailing.Previous(if (quantifier.optional) previous else PatternChars.empty)
    if (previous overlaps repeated.first) return Trailing.Adjacent
    return Trailing.Previous(if (quantifier.optional) PatternChars.union(listOf(previous, repeated.last)) else repeated.last)
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
    val chars = PatternChars.union(closed.parts.map { it.chars })
    val separated = closed.separated(chars)
    if (separated == null && closed.repeats(quantifier)) return Closing.Nested
    val parent = groups.last()
    parent.inner = parent.inner || closed.inner
    parent.unbounded = parent.unbounded || closed.unbounded
    return Closing.Closed(closed, separated ?: Repeated(chars))
}

private sealed interface ScanStep {
    data object Next : ScanStep

    class Done(
        val result: SlowPattern?,
    ) : ScanStep
}

private class SlowPatternScan {
    private val groups = mutableListOf(PatternGroup())

    fun open(look: Boolean) {
        val entry = if (look) PatternChars.empty else groups.last().previous
        groups.add(PatternGroup(look = look, entry = entry))
    }

    fun separate(alternation: Boolean) = groups.last().separate(alternation)

    fun atom(
        atom: String,
        quantifier: PatternQuantifier,
    ): ScanStep {
        val repeated: Repeated
        var closed: PatternGroup? = null
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
            repeated = Repeated(atomChars(atom))
        }
        val current = groups.last()
        current.parts += Part(repeated.chars, quantifier, closed == null)
        if (closed != null && (closed.look || !quantifier.present)) {
            current.previous = if (closed.look || closed.alternated) PatternChars.empty else closed.previous
            return ScanStep.Next
        }
        current.inner = current.inner || !quantifier.exact
        current.unbounded = current.unbounded || quantifier.unbounded
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
