package dev.formhaus.core.unicode

internal class RangeTable(
    encoded: String,
) {
    private val lowers: IntArray
    private val uppers: IntArray

    init {
        val numbers = decodeNumbers(encoded)
        lowers = IntArray(numbers.size / 2)
        uppers = IntArray(numbers.size / 2)
        var next = 0
        for (index in lowers.indices) {
            lowers[index] = next + numbers[2 * index]
            uppers[index] = lowers[index] + numbers[2 * index + 1]
            next = uppers[index] + 1
        }
    }

    val bounds: IntArray get() = IntArray(2 * lowers.size) { if (it % 2 == 0) lowers[it / 2] else uppers[it / 2] }

    operator fun contains(point: Int): Boolean {
        val index = lastAtOrBefore(lowers, point)
        return index >= 0 && point <= uppers[index]
    }
}
