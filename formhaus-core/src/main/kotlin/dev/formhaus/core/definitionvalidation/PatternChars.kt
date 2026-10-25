package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.regex.CharSet
import dev.formhaus.core.regex.IntList

internal class PatternChars private constructor(
    private val bounds: IntArray,
) {
    private val reach by lazy { RangeReach(bounds) }

    private val rangeCount: Int get() = bounds.size / 2

    infix fun overlaps(other: PatternChars): Boolean {
        val (small, large) = if (rangeCount <= other.rangeCount) this to other else other to this
        if (small.rangeCount == 0) return false
        return (0 until small.rangeCount).any { large.reach.meets(small.bounds[2 * it], small.bounds[2 * it + 1]) }
    }

    fun appendTo(ranges: IntList) {
        for (bound in bounds) ranges.add(bound)
    }

    val complement: PatternChars
        get() {
            val order = (0 until rangeCount).sortedBy { bounds[2 * it] }
            val gaps = IntList()
            var next = 0
            for (range in order) {
                val low = bounds[2 * range]
                if (low > next) gaps.addRange(next, low - 1)
                next = maxOf(next, bounds[2 * range + 1] + 1)
            }
            if (next <= CharSet.MAX_UNIT) gaps.addRange(next, CharSet.MAX_UNIT)
            return PatternChars(gaps.toArray())
        }

    companion object {
        val empty: PatternChars = PatternChars(IntArray(0))

        fun of(ranges: IntList): PatternChars = PatternChars(ranges.toArray())

        fun of(set: CharSet): PatternChars =
            PatternChars(IntArray(2 * set.rangeCount) { if (it % 2 == 0) set.lowerAt(it / 2) else set.upperAt(it / 2) })

        fun unit(code: Int): PatternChars = PatternChars(intArrayOf(code, code))

        fun union(sets: List<PatternChars>): PatternChars {
            if (sets.size == 1) return sets[0]
            val ranges = IntList()
            for (set in sets) set.appendTo(ranges)
            return of(ranges)
        }
    }
}

private class RangeReach(
    bounds: IntArray,
) {
    private val lows: IntArray
    private val highs: IntArray

    init {
        val order = (0 until bounds.size / 2).sortedBy { bounds[2 * it] }
        lows = IntArray(order.size) { bounds[2 * order[it]] }
        highs = IntArray(order.size)
        var furthest = Int.MIN_VALUE
        for ((index, range) in order.withIndex()) {
            furthest = maxOf(furthest, bounds[2 * range + 1])
            highs[index] = furthest
        }
    }

    fun meets(
        low: Int,
        high: Int,
    ): Boolean {
        var lower = 0
        var upper = lows.size
        while (lower < upper) {
            val middle = (lower + upper) ushr 1
            if (lows[middle] <= high) lower = middle + 1 else upper = middle
        }
        return lower > 0 && highs[lower - 1] >= low
    }
}

private val all = PatternChars.of(CharSet.all)
private val dot = PatternChars.of(CharSet.dot)
private val classEscapes =
    mapOf(
        'd' to CharSet.digit,
        'D' to CharSet.notDigit,
        'w' to CharSet.word,
        'W' to CharSet.notWord,
        's' to CharSet.space,
        'S' to CharSet.notSpace,
    ).mapValues { PatternChars.of(it.value) }
private val controls = mapOf('n' to 10, 't' to 9, 'r' to 13, 'f' to 12, 'v' to 11)

private const val BACKSLASH = 92
private const val BACKSPACE = 8
private const val CONTROL_MODULUS = 32

private class Read(
    val end: Int,
    val code: Int = 0,
    val set: PatternChars? = null,
)

private fun Char.isAsciiHex(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private fun hex(
    source: String,
    at: Int,
    length: Int,
): Int? =
    source
        .takeIf { at + length <= it.length }
        ?.substring(at, at + length)
        ?.takeIf { it.all(Char::isAsciiHex) }
        ?.toInt(16)

private fun octalEnd(
    source: String,
    at: Int,
): Int {
    val digits =
        when (source[at]) {
            in '0'..'3' -> 3
            in '4'..'7' -> 2
            else -> return at
        }
    var end = at + 1
    while (end < at + digits && source.getOrNull(end)?.let { it in '0'..'7' } == true) end++
    return end
}

private fun coded(
    source: String,
    at: Int,
    inClass: Boolean,
): Read? {
    val char = source[at]
    if (char == 'x' || char == 'u') {
        val length = if (char == 'x') 2 else 4
        return hex(source, at + 1, length)?.let { Read(at + 1 + length, it) }
    }
    if (char == 'c') {
        val letter = source.getOrNull(at + 1)?.takeIf(Char::isAsciiLetter)
        return if (letter != null) Read(at + 2, letter.code % CONTROL_MODULUS) else Read(at, BACKSLASH)
    }
    if (char !in '0'..'9') return null
    if (char != '0' && !inClass) return Read(source.length, set = all)
    val end = octalEnd(source, at)
    return if (end > at) Read(end, source.substring(at, end).toInt(8)) else null
}

private fun escaped(
    source: String,
    at: Int,
    inClass: Boolean,
): Read {
    val char = source.getOrElse(at) { '\\' }
    classEscapes[char]?.let { return Read(at + 1, set = it) }
    controls[char]?.let { return Read(at + 1, it) }
    if (inClass && char == 'b') return Read(at + 1, BACKSPACE)
    if (at < source.length) coded(source, at, inClass)?.let { return it }
    if (char == 'k' && !inClass) return Read(source.length, set = all)
    return Read(at + 1, char.code)
}

private fun member(
    source: String,
    at: Int,
): Read = if (source[at] == '\\') escaped(source, at + 1, true) else Read(at + 1, source[at].code)

private fun characterClass(atom: String): PatternChars {
    val negated = atom.getOrNull(1) == '^'
    val end = atom.length - 1
    val ranges = IntList()
    var at = if (negated) 2 else 1
    while (at < end) {
        val from = member(atom, at)
        at = from.end
        val set = from.set
        val to = if (set == null && atom.getOrNull(at) == '-' && at + 1 < end) member(atom, at + 1) else null
        when {
            set != null -> {
                set.appendTo(ranges)
            }

            to != null && to.set == null -> {
                ranges.addRange(from.code, to.code)
                at = to.end
            }

            else -> {
                ranges.addRange(from.code, from.code)
            }
        }
    }
    val chars = PatternChars.of(ranges)
    return if (negated) chars.complement else chars
}

internal fun atomChars(atom: String): PatternChars =
    when {
        atom[0] == '[' -> characterClass(atom)
        atom == "." -> dot
        atom[0] != '\\' -> PatternChars.unit(atom[0].code)
        else -> escaped(atom, 1, false).let { it.set ?: PatternChars.unit(it.code) }
    }
