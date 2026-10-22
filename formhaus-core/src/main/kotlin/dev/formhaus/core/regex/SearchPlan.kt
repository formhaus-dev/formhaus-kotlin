package dev.formhaus.core.regex

internal sealed interface SearchPlan {
    data object Forward : SearchPlan

    data object FromEnd : SearchPlan

    class Suffix(val search: LiteralSearch) : SearchPlan

    val backward: Boolean get() = this != Forward

    fun end(text: CharArray, previous: Int?): Int? = when (this) {
        Forward -> null
        FromEnd -> if (previous == null) text.size else null
        is Suffix -> {
            val length = search.literal.size
            search.index(text, previous?.let { it - length + 1 } ?: 0)?.let { it + length }
        }
    }

    companion object {
        fun of(root: RegexNode, automaton: Boolean, anchored: Boolean): SearchPlan {
            if (!automaton || anchored) return Forward
            if (endAnchored(root)) return FromEnd
            val suffix = suffix(root).takeLast(Prefilter.LITERAL_LIMIT)
            return if (suffix.isEmpty()) Forward else Suffix(LiteralSearch(suffix.toCharArray()))
        }

        private fun endAnchored(node: RegexNode): Boolean = when (node) {
            is RegexNode.Assertion -> node.assertion == RegexAssertion.INPUT_END
            is RegexNode.Sequence -> node.nodes.lastOrNull { !it.isVoid }?.let(::endAnchored) ?: false
            is RegexNode.Alternation -> node.branches.all(::endAnchored)
            is RegexNode.Group -> endAnchored(node.body)
            is RegexNode.Quantified -> node.quantifier.min > 0 && endAnchored(node.body)
            else -> false
        }

        private fun suffix(node: RegexNode): String = when (node) {
            is RegexNode.Unit -> node.unit.toString()
            is RegexNode.Group -> suffix(node.body)
            is RegexNode.Sequence -> {
                var literal = ""
                for (child in node.nodes.asReversed()) {
                    if (child.isVoid) continue
                    literal = suffix(child) + literal
                    if (!child.isLiteral || literal.length >= Prefilter.LITERAL_LIMIT) break
                }
                literal
            }
            else -> ""
        }
    }
}
