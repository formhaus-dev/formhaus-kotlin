package dev.formhaus.core.regex

internal val RegexNode.children: List<RegexNode>
    get() =
        when (this) {
            is RegexNode.Sequence -> nodes
            is RegexNode.Alternation -> branches
            is RegexNode.Group -> listOf(body)
            is RegexNode.Quantified -> listOf(body)
            is RegexNode.Lookaround -> listOf(body)
            else -> emptyList()
        }

internal fun RegexNode.contains(predicate: (RegexNode) -> Boolean): Boolean = predicate(this) || children.any { it.contains(predicate) }

internal val RegexNode.captureRange: IntRange?
    get() {
        var bounds = (this as? RegexNode.Group)?.capture?.let { it..it }
        for (child in children) {
            val inner = child.captureRange ?: continue
            bounds = bounds?.let { minOf(it.first, inner.first)..maxOf(it.last, inner.last) } ?: inner
        }
        return bounds
    }

internal val RegexNode.canBeEmpty: Boolean
    get() =
        when (this) {
            is RegexNode.Unit, is RegexNode.Set -> false
            is RegexNode.Sequence -> nodes.all { it.canBeEmpty }
            is RegexNode.Alternation -> branches.any { it.canBeEmpty }
            is RegexNode.Group -> body.canBeEmpty
            is RegexNode.Quantified -> quantifier.min == 0 || body.canBeEmpty
            is RegexNode.Assertion, is RegexNode.Lookaround, is RegexNode.Backreference -> true
        }

internal val RegexNode.minimumLength: Int
    get() =
        when (this) {
            is RegexNode.Unit, is RegexNode.Set -> {
                1
            }

            is RegexNode.Sequence -> {
                nodes.fold(0) { total, node -> saturatingSum(total, node.minimumLength) }
            }

            is RegexNode.Alternation -> {
                branches.minOfOrNull { it.minimumLength } ?: 0
            }

            is RegexNode.Group -> {
                body.minimumLength
            }

            is RegexNode.Quantified -> {
                val length = body.minimumLength
                when {
                    length == 0 -> 0
                    quantifier.min > Int.MAX_VALUE / length -> Int.MAX_VALUE
                    else -> quantifier.min * length
                }
            }

            is RegexNode.Assertion, is RegexNode.Lookaround, is RegexNode.Backreference -> {
                0
            }
        }

internal val RegexNode.isVoid: Boolean get() = this is RegexNode.Quantified && quantifier.max == 0

internal val RegexNode.isLiteral: Boolean
    get() =
        when (this) {
            is RegexNode.Unit -> true
            is RegexNode.Group -> body.isLiteral
            is RegexNode.Sequence -> nodes.all { it.isVoid || it.isLiteral }
            else -> false
        }

private fun saturatingSum(
    first: Int,
    second: Int,
): Int = minOf(first.toLong() + second, Int.MAX_VALUE.toLong()).toInt()
