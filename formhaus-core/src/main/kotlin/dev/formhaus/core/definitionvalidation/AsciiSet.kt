package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.regex.CompiledPattern
import dev.formhaus.core.regex.RegexSyntaxError

internal data class AsciiSet(
    private val low: Long = 0,
    private val high: Long = 0,
) {
    val isEmpty: Boolean get() = low == 0L && high == 0L
    val size: Int get() = java.lang.Long.bitCount(low) + java.lang.Long.bitCount(high)

    val single: Char?
        get() =
            when {
                size != 1 -> {
                    null
                }

                low == 0L -> {
                    (64 + java.lang.Long.numberOfTrailingZeros(high)).toChar()
                }

                else -> {
                    java.lang.Long
                        .numberOfTrailingZeros(low)
                        .toChar()
                }
            }

    operator fun contains(char: Char): Boolean =
        when {
            char.code >= 128 -> false
            char.code < 64 -> low and (1L shl char.code) != 0L
            else -> high and (1L shl (char.code - 64)) != 0L
        }

    fun with(char: Char): AsciiSet =
        when {
            char.code >= 128 -> this
            char.code < 64 -> copy(low = low or (1L shl char.code))
            else -> copy(high = high or (1L shl (char.code - 64)))
        }

    infix fun union(other: AsciiSet): AsciiSet = AsciiSet(low or other.low, high or other.high)

    infix fun intersect(other: AsciiSet): AsciiSet = AsciiSet(low and other.low, high and other.high)

    fun intersects(other: AsciiSet): Boolean = !(this intersect other).isEmpty

    companion object {
        val ALL: AsciiSet = AsciiSet(-1L, -1L)

        fun of(chars: String): AsciiSet = chars.fold(AsciiSet()) { set, char -> set.with(char) }
    }
}

internal class AsciiCounts(
    val once: AsciiSet = AsciiSet(),
    private val twice: AsciiSet = AsciiSet(),
) {
    operator fun plus(set: AsciiSet): AsciiCounts = AsciiCounts(once union set, twice union (once intersect set))

    operator fun plus(counts: AsciiCounts): AsciiCounts =
        AsciiCounts(once union counts.once, twice union counts.twice union (once intersect counts.once))

    fun appearsOnce(char: Char): Boolean = char in once && char !in twice
}

internal fun asciiMatches(source: String): AsciiSet {
    val pattern =
        try {
            CompiledPattern(source)
        } catch (_: RegexSyntaxError) {
            return AsciiSet()
        }
    return (0 until 128).fold(AsciiSet()) { set, code ->
        val char = code.toChar()
        if (pattern.test(char.toString()) == true) set.with(char) else set
    }
}
