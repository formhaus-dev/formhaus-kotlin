package dev.formhaus.core.server

import dev.formhaus.core.definitionvalidation.AsciiSet
import dev.formhaus.core.definitionvalidation.asciiMatches

private val punctuation = AsciiSet.of("!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~")
private val special = AsciiSet.of(".^$|()[]{}*+?\\")
private val classEscapes = AsciiSet.of("dws")
private val followers = AsciiSet.of("+*?{")

private enum class Repeat { ONCE, OPTIONAL, PLUS, STAR }

private class Item(
    val chars: AsciiSet,
    val repeat: Repeat,
)

private class ParsedItem(
    val item: Item,
    val next: Int,
)

private class QuadraticScanner(
    val units: String,
) {
    fun unit(index: Int): Char? = units.getOrNull(index)

    private fun Char.isPrintable(): Boolean = this in ' '..'~'

    private fun escapeEnd(index: Int): Int? {
        val next = unit(index + 1) ?: return null
        return if (next in punctuation || next in classEscapes) index + 2 else null
    }

    private fun classMember(index: Int): Int? {
        val char = unit(index) ?: return null
        if (char == '\\') return escapeEnd(index)
        return if (char != ']' && char.isPrintable()) index + 1 else null
    }

    private fun single(
        from: Int,
        to: Int,
    ): Boolean = to - from == 1 || units[from + 1] !in classEscapes

    private fun classEnd(start: Int): Int? {
        var index = start + 1
        if (unit(index) == '^' || unit(index) == ']') return null
        while (unit(index) != ']') {
            val end = classMember(index) ?: return null
            val after = unit(end + 1)
            if (unit(end) != '-' || after == null || after == ']') {
                index = end
                continue
            }
            val last = classMember(end + 1) ?: return null
            if (!single(index, end) || !single(end + 1, last) || units[end - 1] > units[last - 1]) return null
            index = last
        }
        return index + 1
    }

    private fun atomEnd(index: Int): Int? {
        val char = unit(index) ?: return null
        return when {
            char == '[' -> classEnd(index)
            char == '\\' -> escapeEnd(index)
            char.isPrintable() && char !in special -> index + 1
            else -> null
        }
    }

    fun item(
        index: Int,
        end: Int,
    ): ParsedItem? {
        val atomStop = atomEnd(index)?.takeIf { it <= end } ?: return null
        val repeat = if (atomStop < end) repeatOf(units[atomStop]) else Repeat.ONCE
        val next = if (repeat == Repeat.ONCE) atomStop else atomStop + 1
        if (repeat != Repeat.ONCE && next < end && units[next] in followers) return null
        if (repeat == Repeat.ONCE && unit(atomStop) == '{') return null
        return ParsedItem(Item(asciiMatches("^${units.substring(index, atomStop)}$"), repeat), next)
    }

    fun opening(index: Int): Int =
        when {
            units.startsWith("(?:", index) -> 3
            unit(index) == '(' && unit(index + 1) != '?' -> 1
            else -> 0
        }
}

private fun repeatOf(char: Char?): Repeat =
    when (char) {
        '+' -> Repeat.PLUS
        '*' -> Repeat.STAR
        '?' -> Repeat.OPTIONAL
        else -> Repeat.ONCE
    }

private fun items(pattern: String): List<Item>? {
    if (!pattern.startsWith('^')) return null
    val scanner = QuadraticScanner(pattern)
    val end = if (pattern.endsWith('$') && !pattern.endsWith("\\$")) pattern.length - 1 else pattern.length
    val result = ArrayList<Item>()
    var grouped = false
    var index = 1
    while (index < end) {
        val open = scanner.opening(index)
        if (open > 0) {
            if (grouped) return null
            grouped = true
            index += open
        } else if (pattern[index] == ')') {
            val next = scanner.unit(index + 1)
            if (!grouped || next == null || next in followers) return null
            grouped = false
            index += 1
        } else {
            val parsed = scanner.item(index, end) ?: return null
            result += parsed.item
            index = parsed.next
        }
    }
    return if (grouped) null else result
}

internal fun isQuadratic(pattern: String): Boolean {
    val sequence = items(pattern) ?: return false
    var previous = AsciiSet()
    var overlaps = 0
    var optionalOverlaps = 0
    for (item in sequence) {
        val overlapping = item.chars.intersects(previous)
        when (item.repeat) {
            Repeat.PLUS, Repeat.STAR -> {
                if (overlapping) overlaps++
                previous = if (item.repeat == Repeat.STAR) previous union item.chars else item.chars
            }

            Repeat.OPTIONAL -> {
                if (overlapping) optionalOverlaps++
                previous = previous union item.chars
            }

            Repeat.ONCE -> {
                if (overlapping) return false
                previous = AsciiSet()
            }
        }
    }
    return overlaps == 1 && optionalOverlaps <= 1
}
