package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.regex.CompiledPattern
import dev.formhaus.core.regex.RegexSyntaxError

internal enum class SlowPattern { NESTED, ADJACENT }

private class PatternGroup(
    val start: Int,
    val look: Boolean = false,
    var inner: Boolean = false,
    var alternated: Boolean = false,
    val entry: Set<Char>? = null,
    var previous: Set<Char>? = entry,
) {
    fun separate(alternation: Boolean) {
        inner = inner || alternation
        alternated = alternated || alternation
        previous = if (alternation) entry else null
    }
}

private sealed interface Trailing {
    data object Adjacent : Trailing

    class Previous(
        val samples: Set<Char>?,
    ) : Trailing
}

private sealed interface Closing {
    data object Invalid : Closing

    data object Nested : Closing

    data object Settled : Closing

    class Repeated(
        val start: Int,
        val end: Int,
    ) : Closing
}

private fun samples(atom: String): Set<Char> {
    val pattern =
        try {
            CompiledPattern("^(?:$atom)$")
        } catch (_: RegexSyntaxError) {
            return emptySet()
        }
    return (0 until 128).map(Int::toChar).filter { pattern.test(it.toString()) == true }.toSet()
}

private fun trailing(
    group: PatternGroup,
    quantifier: PatternQuantifier,
    source: String,
): Trailing {
    if (!quantifier.unbounded) return Trailing.Previous(if (quantifier.optional) group.previous else null)
    val repeated = samples(source)
    val previous = group.previous
    if (previous != null && previous.any { it in repeated }) return Trailing.Adjacent
    return Trailing.Previous(if (quantifier.optional) repeated + previous.orEmpty() else repeated)
}

private fun close(
    groups: MutableList<PatternGroup>,
    quantifier: PatternQuantifier,
    end: Int,
): Closing {
    if (groups.size < 2) return Closing.Invalid
    val closed = groups.removeAt(groups.size - 1)
    val parent = groups.last()
    if (closed.inner && quantifier.repeats) return Closing.Nested
    parent.inner = parent.inner || closed.inner
    if (closed.look || !quantifier.present) {
        parent.previous = if (closed.look || closed.alternated) null else closed.previous
        return Closing.Settled
    }
    return Closing.Repeated(closed.start, end)
}

internal fun slowPattern(pattern: String): SlowPattern? {
    val lexer = PatternLexer(pattern.toCharArray())
    val units = lexer.units
    if (units.size > 1000) return null
    val groups = mutableListOf(PatternGroup(0))
    var index = 0
    while (index < units.size) {
        val token = lexer.token(index)
        val start = index
        index = token.end
        when (val kind = token.kind) {
            is TokenKind.Open -> {
                val entry = if (kind.look) null else groups.last().previous
                groups.add(PatternGroup(start, look = kind.look, entry = entry))
            }

            TokenKind.Other -> {
                groups.last().separate(alternation = units[start] == '|')
            }

            is TokenKind.Atom -> {
                val quantifier = lexer.quantifier(kind.end)
                index = quantifier.end
                var from = start
                var to = kind.end
                if (units[start] == ')') {
                    when (val closing = close(groups, quantifier, kind.end)) {
                        Closing.Invalid -> {
                            return null
                        }

                        Closing.Nested -> {
                            return SlowPattern.NESTED
                        }

                        Closing.Settled -> {
                            continue
                        }

                        is Closing.Repeated -> {
                            from = closing.start
                            to = closing.end
                        }
                    }
                }
                val current = groups.last()
                current.inner = current.inner || quantifier.present
                when (val next = trailing(current, quantifier, String(units, from, to - from))) {
                    Trailing.Adjacent -> return SlowPattern.ADJACENT
                    is Trailing.Previous -> current.previous = next.samples
                }
            }
        }
    }
    return null
}
