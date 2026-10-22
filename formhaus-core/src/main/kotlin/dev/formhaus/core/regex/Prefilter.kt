package dev.formhaus.core.regex

internal class Prefilter(root: RegexNode) {
    private class First(val set: CharSet, val nullable: Boolean)

    val minimumLength: Int = root.minimumLength
    val required: LiteralSearch = LiteralSearch(required(root).toCharArray())
    val anchored: Boolean = anchored(root)
    val first: CharSet? = first(root)?.takeIf { !it.nullable }?.set

    fun admits(text: CharArray): Boolean = text.size >= minimumLength && required.index(text, 0) != null

    companion object {
        const val LITERAL_LIMIT: Int = 64

        private val nothing = First(CharSet.empty, nullable = true)

        private fun anchored(node: RegexNode): Boolean = when (node) {
            is RegexNode.Assertion -> node.assertion == RegexAssertion.INPUT_START
            is RegexNode.Sequence -> node.nodes.firstOrNull()?.let(::anchored) ?: false
            is RegexNode.Alternation -> node.branches.all(::anchored)
            is RegexNode.Group -> anchored(node.body)
            is RegexNode.Quantified -> node.quantifier.min > 0 && anchored(node.body)
            else -> false
        }

        private fun first(node: RegexNode): First? = when (node) {
            is RegexNode.Unit -> First(CharSet.unit(node.unit), nullable = false)
            is RegexNode.Set -> First(node.set, nullable = false)
            is RegexNode.Sequence -> sequenceFirst(node.nodes)
            is RegexNode.Alternation -> alternationFirst(node.branches)
            is RegexNode.Group -> first(node.body)
            is RegexNode.Quantified ->
                if (node.quantifier.max == 0) {
                    nothing
                } else {
                    first(node.body)?.let { First(it.set, it.nullable || node.quantifier.min == 0) }
                }
            is RegexNode.Assertion, is RegexNode.Lookaround -> nothing
            is RegexNode.Backreference -> null
        }

        private fun sequenceFirst(nodes: List<RegexNode>): First? {
            var set = CharSet.empty
            for (node in nodes) {
                val next = first(node) ?: return null
                set = set.union(next.set)
                if (!next.nullable) return First(set, nullable = false)
            }
            return First(set, nullable = true)
        }

        private fun alternationFirst(nodes: List<RegexNode>): First? {
            var set = CharSet.empty
            var nullable = false
            for (node in nodes) {
                val next = first(node) ?: return null
                set = set.union(next.set)
                nullable = nullable || next.nullable
            }
            return First(set, nullable)
        }

        private fun exact(node: RegexNode): String? = when (node) {
            is RegexNode.Unit -> node.unit.toString()
            is RegexNode.Assertion, is RegexNode.Lookaround -> ""
            is RegexNode.Group -> exact(node.body)
            is RegexNode.Sequence -> {
                val literal = StringBuilder()
                for (child in node.nodes) {
                    val part = exact(child)
                    if (part == null || literal.length + part.length > LITERAL_LIMIT) return null
                    literal.append(part)
                }
                literal.toString()
            }
            else -> null
        }

        private fun required(node: RegexNode): String {
            exact(node)?.let { return it }
            return when (node) {
                is RegexNode.Group -> required(node.body)
                is RegexNode.Quantified -> if (node.quantifier.min > 0) required(node.body) else ""
                is RegexNode.Lookaround -> if (node.look.negated) "" else required(node.body)
                is RegexNode.Sequence -> {
                    var best = ""
                    var run = ""
                    for (child in node.nodes) {
                        val part = exact(child)
                        if (part != null && run.length + part.length <= LITERAL_LIMIT) {
                            run += part
                            continue
                        }
                        best = longer(longer(best, run), required(child))
                        run = part ?: ""
                    }
                    longer(best, run)
                }
                else -> ""
            }
        }

        private fun longer(first: String, second: String): String = if (second.length > first.length) second else first
    }
}
