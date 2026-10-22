package dev.formhaus.core.json

private val wideWhitespace = setOf(
    ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
    ' ', ' ', ' ', ' ', ' ', ' ', '　', '﻿',
)

internal fun isJsWhitespace(char: Char): Boolean =
    char == ' ' || char in '\u0009'..'\u000D' || (char >= ' ' && char in wideWhitespace)

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

internal fun asciiDigit(char: Char, radix: Int): Int {
    val digit = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'z' -> char - 'a' + 10
        in 'A'..'Z' -> char - 'A' + 10
        else -> return -1
    }
    return if (digit < radix) digit else -1
}

private val radixPrefixes = listOf("0x" to 16, "0o" to 8, "0b" to 2)

internal fun jsNumber(text: String): Double {
    val trimmed = text.jsTrimmed
    when (trimmed) {
        "" -> return 0.0
        "Infinity", "+Infinity" -> return Double.POSITIVE_INFINITY
        "-Infinity" -> return Double.NEGATIVE_INFINITY
    }
    for ((prefix, radix) in radixPrefixes) {
        if (!trimmed.startsWith(prefix, ignoreCase = true)) continue
        val body = trimmed.substring(2)
        if (body.isEmpty() || body.any { asciiDigit(it, radix) < 0 }) return Double.NaN
        return body.fold(0.0) { total, char -> total * radix + asciiDigit(char, radix) }
    }
    return if (isDecimalLiteral(trimmed)) trimmed.toDouble() else Double.NaN
}

private fun isDecimalLiteral(text: String): Boolean {
    var index = if (text.startsWith('+') || text.startsWith('-')) 1 else 0
    fun digits(): Int {
        val from = index
        while (index < text.length && text[index] in '0'..'9') index++
        return index - from
    }
    var count = digits()
    if (index < text.length && text[index] == '.') {
        index++
        count += digits()
    }
    if (count == 0) return false
    if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
        index++
        if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
        if (digits() == 0) return false
    }
    return index == text.length
}
