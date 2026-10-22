package dev.formhaus.core.regex

internal sealed interface RegexNode {
    data class Unit(val unit: Char) : RegexNode

    data class Set(val set: CharSet) : RegexNode

    data class Sequence(val nodes: List<RegexNode>) : RegexNode

    data class Alternation(val branches: List<RegexNode>) : RegexNode

    data class Group(val body: RegexNode, val capture: Int?) : RegexNode

    data class Quantified(val body: RegexNode, val quantifier: Quantifier) : RegexNode

    data class Assertion(val assertion: RegexAssertion) : RegexNode

    data class Lookaround(val body: RegexNode, val look: Look) : RegexNode

    data class Backreference(val groups: List<Int>, val ignoreCase: Boolean) : RegexNode
}

internal data class Quantifier(val min: Int, val max: Int?, val greedy: Boolean)

internal data class Look(val behind: Boolean, val negated: Boolean, val index: Int)

internal enum class RegexAssertion { INPUT_START, INPUT_END, LINE_START, LINE_END, WORD_BOUNDARY, NOT_WORD_BOUNDARY }

internal data class RegexModifiers(val ignoreCase: Boolean = false, val multiline: Boolean = false, val dotAll: Boolean = false)

internal object RegexSyntaxError : Exception(null, null, false, false)
