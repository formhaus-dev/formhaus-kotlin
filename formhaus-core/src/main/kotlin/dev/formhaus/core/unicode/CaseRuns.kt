package dev.formhaus.core.unicode

internal class CaseRuns(
    encoded: String,
) {
    private val starts: IntArray
    private val lasts: IntArray
    private val steps: IntArray
    private val deltas: IntArray

    init {
        val numbers = decodeNumbers(encoded)
        val count = numbers.size / FIELDS
        starts = IntArray(count)
        lasts = IntArray(count)
        steps = IntArray(count)
        deltas = IntArray(count)
        var next = 0
        for (index in 0 until count) {
            val base = FIELDS * index
            val zigzag = numbers[base + 3]
            starts[index] = next + numbers[base]
            steps[index] = numbers[base + 2] + 1
            lasts[index] = starts[index] + numbers[base + 1] * steps[index]
            deltas[index] = if (zigzag % 2 == 0) zigzag / 2 else -(zigzag + 1) / 2
            next = lasts[index] + 1
        }
    }

    fun map(point: Int): Int {
        val index = lastAtOrBefore(starts, point)
        if (index < 0 || point > lasts[index] || (point - starts[index]) % steps[index] != 0) return point
        return point + deltas[index]
    }

    fun forEachMapped(body: (point: Int, target: Int) -> Unit) {
        for (index in starts.indices) {
            for (point in starts[index]..lasts[index] step steps[index]) body(point, point + deltas[index])
        }
    }

    private companion object {
        const val FIELDS = 4
    }
}
