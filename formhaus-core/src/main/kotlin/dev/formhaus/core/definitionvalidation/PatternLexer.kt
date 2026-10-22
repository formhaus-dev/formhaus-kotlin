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
    val present: Boolean = false,
    val repeats: Boolean = false,
    val unbounded: Boolean = false,
    val optional: Boolean = false,
) {
    fun ending(end: Int): PatternQuantifier = PatternQuantifier(end, present, repeats, unbounded, optional)
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
                if (units[close] ==
                    '>'
                ) {
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
        return if (lower == 'b') null else index + 2
    }

    private fun hex(
        start: Int,
        count: Int,
    ): Boolean =
        (start until start + count).all { index ->
            unit(index)?.let { it in '0'..'9' || it.lowercaseAscii() in 'a'..'f' } ?: false
        }

    private fun digitsEnd(start: Int): Int {
        var index = start
        while (unit(index)?.let { it in '0'..'9' } == true) index++
        return index
    }

    fun quantifier(start: Int): PatternQuantifier {
        val char = unit(start) ?: return PatternQuantifier(start)
        val result =
            when {
                char == '*' || char == '+' || char == '?' -> {
                    PatternQuantifier(
                        start + 1,
                        present = true,
                        repeats = char != '?',
                        unbounded = char != '?',
                        optional = char != '+',
                    )
                }

                char == '{' -> {
                    braced(start) ?: return PatternQuantifier(start)
                }

                else -> {
                    return PatternQuantifier(start)
                }
            }
        return if (unit(result.end) == '?') result.ending(result.end + 1) else result
    }

    private fun braced(start: Int): PatternQuantifier? {
        val minimumEnd = digitsEnd(start + 1)
        if (minimumEnd <= start + 1) return null
        var index = minimumEnd
        var open = false
        if (unit(index) == ',') {
            index = digitsEnd(index + 1)
            open = index == minimumEnd + 1
        }
        if (unit(index) != '}') return null
        val zero = minimumEnd == start + 2 && unit(start + 1) == '0'
        return PatternQuantifier(index + 1, present = true, repeats = true, unbounded = open, optional = zero)
    }

    private companion object {
        val lookMarks = setOf('=', '!')

        fun Char.lowercaseAscii(): Char = (code or 0x20).toChar()
    }
}
