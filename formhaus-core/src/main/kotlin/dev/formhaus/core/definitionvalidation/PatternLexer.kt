package dev.formhaus.core.definitionvalidation

internal sealed interface TokenKind {
    class Open(
        val look: Boolean,
    ) : TokenKind

    class Atom(
        val end: Int,
    ) : TokenKind

    data object Other : TokenKind
}

internal class Token(
    val kind: TokenKind,
    val end: Int,
)

internal class PatternQuantifier(
    val end: Int,
    private val kind: Char? = null,
    private val min: String = "",
    private val max: String? = null,
) {
    val present: Boolean get() = kind != null
    val repeats: Boolean get() = kind != null && kind != '?'
    val unbounded: Boolean get() = kind == '*' || kind == '+' || max == ""
    val optional: Boolean get() = kind == '*' || kind == '?' || min == "0"
    val exact: Boolean get() = kind == null || (kind == '{' && (max == null || max == min))
    val mandatory: Boolean get() = exact || kind == '+' || (max != null && min.first() != '0')
    val upper: Int get() = max?.toIntOrNull() ?: Int.MAX_VALUE

    fun ending(end: Int): PatternQuantifier = PatternQuantifier(end, kind, min, max)
}

internal class PatternLexer(
    val units: CharArray,
) {
    private val classEnds = IntArray(units.size + 2) { -1 }

    init {
        for (index in units.indices.reversed()) {
            classEnds[index] =
                when (units[index]) {
                    ']' -> index + 1
                    '\\' -> classEnds[index + 2].takeIf { it >= 0 } ?: classEnds[index + 1]
                    else -> classEnds[index + 1]
                }
        }
    }

    fun unit(index: Int): Char? = units.getOrNull(index)

    fun token(index: Int): Token {
        val char = units[index]
        if (char == '(') return open(index)
        atomEnd(index)?.let { return Token(TokenKind.Atom(it), it) }
        return Token(TokenKind.Other, if (char == '\\' && index + 1 < units.size) index + 2 else index + 1)
    }

    private fun open(index: Int): Token {
        if (unit(index + 1) != '?') return Token(TokenKind.Open(false), index + 1)
        val next = index + 2
        if (unit(next) in lookMarks) return Token(TokenKind.Open(true), next + 1)
        if (unit(next) == '<' && unit(next + 1) in lookMarks) return Token(TokenKind.Open(true), next + 2)
        if (unit(next) == ':') return Token(TokenKind.Open(false), next + 1)
        if (unit(next) == '<') {
            for (close in next + 1 until units.size) {
                if (units[close] == '>') {
                    return Token(TokenKind.Open(false), close + 1)
                }
            }
        }
        return Token(TokenKind.Open(false), index + 1)
    }

    private fun atomEnd(index: Int): Int? =
        when (units[index]) {
            ')' -> index + 1
            '\\' -> escapeEnd(index)
            '[' -> classEnds[index + 1].takeIf { it >= 0 } ?: (index + 1)
            '|', '^', '$' -> null
            else -> index + 1
        }

    private fun escapeEnd(index: Int): Int? {
        val char = unit(index + 1) ?: return null
        val lower = char.lowercaseAscii()
        if (lower == 'x' && hex(index + 2, 2)) return index + 4
        if (lower == 'u' && hex(index + 2, 4)) return index + 6
        if (lower == 'c' && unit(index + 2)?.lowercaseAscii() in 'a'..'z') return index + 3
        if (char in '0'..'9') return digitsEnd(index + 1)
        if (lower == 'k') groupNameEnd(index + 2)?.let { return it }
        return if (lower == 'b') null else index + 2
    }

    private fun groupNameEnd(start: Int): Int? {
        if (unit(start) != '<') return null
        var index = start + 1
        while (true) index = nameUnitEnd(index) ?: break
        return if (index > start + 1 && unit(index) == '>') index + 1 else null
    }

    private fun nameUnitEnd(index: Int): Int? {
        val char = unit(index) ?: return null
        if (char == '$' || char.code >= 0x80 || char.isAsciiWord()) return index + 1
        if (char != '\\' || unit(index + 1)?.lowercaseAscii() != 'u') return null
        if (hex(index + 2, 4)) return index + 6
        if (unit(index + 2) != '{') return null
        val close = hexEnd(index + 3)
        return if (close > index + 3 && unit(close) == '}') close + 1 else null
    }

    private fun isHex(index: Int): Boolean = unit(index)?.let { it in '0'..'9' || it.lowercaseAscii() in 'a'..'f' } ?: false

    private fun hex(
        start: Int,
        count: Int,
    ): Boolean = (start until start + count).all(::isHex)

    private fun hexEnd(start: Int): Int {
        var index = start
        while (isHex(index)) index++
        return index
    }

    private fun digitsEnd(start: Int): Int {
        var index = start
        while (unit(index)?.let { it in '0'..'9' } == true) index++
        return index
    }

    fun quantifier(start: Int): PatternQuantifier {
        val char = unit(start) ?: return PatternQuantifier(start)
        val result =
            when (char) {
                '*', '+', '?' -> PatternQuantifier(start + 1, char)
                '{' -> braced(start) ?: return PatternQuantifier(start)
                else -> return PatternQuantifier(start)
            }
        return if (unit(result.end) == '?') result.ending(result.end + 1) else result
    }

    private fun braced(start: Int): PatternQuantifier? {
        val minimumEnd = digitsEnd(start + 1)
        if (minimumEnd <= start + 1) return null
        val min = String(units, start + 1, minimumEnd - start - 1)
        if (unit(minimumEnd) == '}') return PatternQuantifier(minimumEnd + 1, '{', min)
        if (unit(minimumEnd) != ',') return null
        val maximumEnd = digitsEnd(minimumEnd + 1)
        if (unit(maximumEnd) != '}') return null
        return PatternQuantifier(maximumEnd + 1, '{', min, String(units, minimumEnd + 1, maximumEnd - minimumEnd - 1))
    }

    private companion object {
        val lookMarks = setOf('=', '!')

        fun Char.lowercaseAscii(): Char = (code or 0x20).toChar()
    }
}

private fun Char.isAsciiWord(): Boolean = this in '0'..'9' || this in 'A'..'Z' || this in 'a'..'z' || this == '_'
