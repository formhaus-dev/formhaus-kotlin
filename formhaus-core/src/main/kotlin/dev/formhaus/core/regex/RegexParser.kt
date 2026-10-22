package dev.formhaus.core.regex

internal class ParsedPattern(
    val root: RegexNode,
    val captureCount: Int,
)

internal class RegexParser private constructor(
    val units: CharArray,
) {
    class Atom(
        val node: RegexNode,
        val quantifiable: Boolean,
    )

    class NamedGroup(
        val name: String,
        val index: Int,
    )

    private class Bounds(
        val min: Int,
        val max: Int?,
        val end: Int,
    )

    val groups: CaptureScan = CaptureScan(units)
    var index: Int = 0
    var modifiers: RegexModifiers = RegexModifiers()
    var captures: Int = 0
    var looks: Int = 0
    var scope: NameScope = NameScope(0)
    val named: MutableList<NamedGroup> = ArrayList()
    private var depth = 0

    companion object {
        const val MAX_DEPTH: Int = 100
        private const val INFINITY = Int.MAX_VALUE

        fun parse(source: String): ParsedPattern {
            val parser = RegexParser(source.toCharArray())
            val root = parser.parseDisjunction()
            if (parser.index != parser.units.size) throw RegexSyntaxError
            return ParsedPattern(root, parser.captures)
        }
    }

    fun peek(offset: Int = 0): Char? = units.getOrNull(index + offset)

    fun at(
        char: Char,
        offset: Int = 0,
    ): Boolean = peek(offset) == char

    fun consume(char: Char): Boolean {
        if (!at(char)) return false
        index++
        return true
    }

    fun parseDisjunction(): RegexNode {
        depth++
        try {
            if (depth > MAX_DEPTH) throw RegexSyntaxError
            val branches = mutableListOf(parseAlternative())
            while (consume('|')) {
                scope = scope.addingAlternative(captures)
                branches.add(parseAlternative())
            }
            return branches.singleOrNull() ?: RegexNode.Alternation(branches)
        } finally {
            depth--
        }
    }

    private fun parseAlternative(): RegexNode {
        val terms = ArrayList<RegexNode>()
        while (peek() != null && !at('|') && !at(')')) terms.add(parseTerm())
        return terms.singleOrNull() ?: RegexNode.Sequence(terms)
    }

    private fun parseTerm(): RegexNode {
        val atom = parseAtom()
        val quantifier = parseQuantifier() ?: return atom.node
        if (!atom.quantifiable) throw RegexSyntaxError
        return RegexNode.Quantified(atom.node, quantifier)
    }

    private fun parseQuantifier(): Quantifier? {
        val bounds =
            when (peek()) {
                '*' -> Bounds(0, null, index + 1)
                '+' -> Bounds(1, null, index + 1)
                '?' -> Bounds(0, 1, index + 1)
                '{' -> bracedBounds(index) ?: return null
                else -> return null
            }
        if (bounds.max != null && bounds.max < bounds.min) throw RegexSyntaxError
        index = bounds.end
        return Quantifier(bounds.min, bounds.max, greedy = !consume('?'))
    }

    fun isBracedQuantifier(start: Int): Boolean = bracedBounds(start) != null

    private fun bracedBounds(start: Int): Bounds? {
        var cursor = start + 1
        val min = number(cursor) ?: return null
        cursor = min.end
        var max: Int? = min.value
        if (cursor < units.size && units[cursor] == ',') {
            cursor++
            val upper = number(cursor)
            max = upper?.value ?: INFINITY
            if (upper != null) cursor = upper.end
        }
        if (cursor >= units.size || units[cursor] != '}') return null
        return Bounds(min.value, max.takeIf { it != INFINITY }, cursor + 1)
    }

    private class Number(
        val value: Int,
        val end: Int,
    )

    private fun number(start: Int): Number? {
        var cursor = start
        var value = 0L
        while (cursor < units.size && units[cursor] in '0'..'9') {
            value = minOf(value * 10 + (units[cursor] - '0'), INFINITY.toLong())
            cursor++
        }
        return if (cursor > start) Number(value.toInt(), cursor) else null
    }
}
