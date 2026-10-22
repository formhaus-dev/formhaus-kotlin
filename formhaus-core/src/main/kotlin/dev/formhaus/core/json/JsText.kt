package dev.formhaus.core.json

import dev.formhaus.core.unicode.whiteSpace

private const val NO_BREAK_SPACE = 0xA0

internal fun isJsWhitespace(char: Char): Boolean =
    char == ' ' || char in '\u0009'..'\u000D' || (char.code >= NO_BREAK_SPACE && char.code in whiteSpace)

internal val String.jsTrimmed: String get() = trim(::isJsWhitespace)

internal val String.isBlankText: Boolean get() = all(::isJsWhitespace)

internal val String.jsCollapsed: String
    get() {
        if (isSettled()) return this
        val result = StringBuilder(length)
        var previous = false
        for (char in this) {
            val space = isJsWhitespace(char)
            if (!space || !previous) result.append(if (space) ' ' else char)
            previous = space
        }
        return result.toString()
    }

private fun String.isSettled(): Boolean {
    var previous = false
    for (char in this) {
        val space = isJsWhitespace(char)
        if (space && (previous || char != ' ')) return false
        previous = space
    }
    return true
}

internal fun asciiDigit(
    char: Char,
    radix: Int,
): Int {
    val digit =
        when (char) {
            in '0'..'9' -> char - '0'
            in 'a'..'z' -> char - 'a' + 10
            in 'A'..'Z' -> char - 'A' + 10
            else -> return -1
        }
    return if (digit < radix) digit else -1
}

private val radixes = mapOf('x' to 16, 'o' to 8, 'b' to 2, 'X' to 16, 'O' to 8, 'B' to 2)

internal fun jsNumber(text: String): Double {
    val trimmed = text.jsTrimmed
    when (trimmed) {
        "" -> return 0.0
        "Infinity", "+Infinity" -> return Double.POSITIVE_INFINITY
        "-Infinity" -> return Double.NEGATIVE_INFINITY
    }
    val radix = if (trimmed.length >= 2 && trimmed[0] == '0') radixes[trimmed[1]] else null
    if (radix != null) {
        val body = trimmed.substring(2)
        if (body.isEmpty() || body.any { asciiDigit(it, radix) < 0 }) return Double.NaN
        return body.fold(0.0) { total, char -> total * radix + asciiDigit(char, radix) }
    }
    return if (isDecimalLiteral(trimmed)) trimmed.toDouble() else Double.NaN
}

private fun skipDigits(
    text: String,
    from: Int,
): Int {
    var index = from
    while (index < text.length && text[index] in '0'..'9') index++
    return index
}

private fun skipExponent(
    text: String,
    from: Int,
): Int {
    if (from >= text.length || text[from] !in "eE") return from
    val signed = if (from + 1 < text.length && text[from + 1] in "+-") from + 2 else from + 1
    val end = skipDigits(text, signed)
    return if (end == signed) -1 else end
}

private fun isDecimalLiteral(text: String): Boolean {
    val start = if (text.startsWith('+') || text.startsWith('-')) 1 else 0
    val whole = skipDigits(text, start)
    val hasPoint = whole < text.length && text[whole] == '.'
    val fraction = if (hasPoint) skipDigits(text, whole + 1) else whole
    val digits = (whole - start) + (if (hasPoint) fraction - whole - 1 else 0)
    return digits > 0 && skipExponent(text, fraction) == text.length
}
