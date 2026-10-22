package dev.formhaus.core.regex

internal class CharSet private constructor(
    private val bounds: IntArray,
) {
    private val low: Long
    private val high: Long

    init {
        var low = 0L
        var high = 0L
        for (index in 0 until rangeCount) {
            val lower = lowerAt(index)
            if (lower >= 128) break
            val upper = minOf(upperAt(index), 127)
            if (lower < 64) low = low or mask(lower, minOf(upper, 63))
            if (upper >= 64) high = high or mask(maxOf(lower, 64) - 64, upper - 64)
        }
        this.low = low
        this.high = high
    }

    val rangeCount: Int get() = bounds.size / 2

    fun lowerAt(index: Int): Int = bounds[2 * index]

    fun upperAt(index: Int): Int = bounds[2 * index + 1]

    val singleUnit: Char?
        get() = if (rangeCount == 1 && bounds[0] == bounds[1]) bounds[0].toChar() else null

    operator fun contains(unit: Char): Boolean = containsCode(unit.code)

    fun containsCode(code: Int): Boolean {
        if (code < 0) return false
        if (code < 64) return (low ushr code) and 1L == 1L
        if (code < 128) return (high ushr (code - 64)) and 1L == 1L
        var lower = 0
        var upper = rangeCount
        while (lower < upper) {
            val middle = (lower + upper) ushr 1
            if (upperAt(middle) < code) lower = middle + 1 else upper = middle
        }
        return lower < rangeCount && lowerAt(lower) <= code
    }

    fun union(other: CharSet): CharSet = of(bounds + other.bounds)

    val inverted: CharSet
        get() {
            val gaps = IntList()
            var next = 0
            for (index in 0 until rangeCount) {
                if (lowerAt(index) > next) gaps.addRange(next, lowerAt(index) - 1)
                next = upperAt(index) + 1
            }
            if (next <= MAX_UNIT) gaps.addRange(next, MAX_UNIT)
            return of(gaps.toArray())
        }

    override fun equals(other: Any?): Boolean = other is CharSet && bounds.contentEquals(other.bounds)

    override fun hashCode(): Int = bounds.contentHashCode()

    companion object {
        const val MAX_UNIT: Int = 0xFFFF

        fun of(bounds: IntArray): CharSet = CharSet(if (isNormal(bounds)) bounds else normalized(bounds))

        fun of(vararg ranges: CharRange): CharSet =
            of(IntArray(2 * ranges.size) { if (it % 2 == 0) ranges[it / 2].first.code else ranges[it / 2].last.code })

        fun unit(unit: Char): CharSet = CharSet(intArrayOf(unit.code, unit.code))

        val empty: CharSet = CharSet(IntArray(0))
        val all: CharSet = of('\u0000'..'￿')
        val digit: CharSet = of('0'..'9')
        val word: CharSet = of('0'..'9', 'A'..'Z', '_'..'_', 'a'..'z')
        val lineTerminator: CharSet = of('\n'..'\n', '\r'..'\r', ' '..' ')
        val dot: CharSet = lineTerminator.inverted
        val notDigit: CharSet = digit.inverted
        val notWord: CharSet = word.inverted
        val space: CharSet =
            of(
                '\u0009'..'\u000D',
                ' '..' ',
                ' '..' ',
                ' '..' ',
                ' '..' ',
                ' '..' ',
                ' '..' ',
                ' '..' ',
                '　'..'　',
                '\uFEFF'..'\uFEFF',
            )
        val notSpace: CharSet = space.inverted

        private fun mask(
            lower: Int,
            upper: Int,
        ): Long {
            val width = upper - lower + 1
            return (if (width == 64) -1L else (1L shl width) - 1) shl lower
        }

        private fun isNormal(bounds: IntArray): Boolean {
            var index = 3
            while (index < bounds.size) {
                if (bounds[index - 2] + 1 >= bounds[index - 1]) return false
                index += 2
            }
            return true
        }

        private fun normalized(bounds: IntArray): IntArray {
            val order = (0 until bounds.size / 2).sortedBy { bounds[2 * it] }
            val merged = IntList()
            for (range in order) {
                val lower = bounds[2 * range]
                val upper = bounds[2 * range + 1]
                val last = merged.size - 1
                if (last > 0 && lower <= merged[last] + 1) {
                    merged[last] = maxOf(merged[last], upper)
                } else {
                    merged.addRange(lower, upper)
                }
            }
            return merged.toArray()
        }
    }
}
