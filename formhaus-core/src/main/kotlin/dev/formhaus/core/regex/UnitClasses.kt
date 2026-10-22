package dev.formhaus.core.regex

internal class UnitClasses(
    program: RegexProgram,
) {
    val starts: IntArray
    private val latin = IntArray(LATIN)

    init {
        val boundaries = boundaries(program.sets)
        for (instruction in program.instructions) {
            if (instruction !is Instruction.Unit) continue
            boundaries.add(instruction.unit.code)
            boundaries.add(instruction.unit.code + 1)
        }
        boundaries.sort()
        starts = merged(boundaries, shared)
        for ((index, start) in starts.withIndex()) {
            if (start >= LATIN) break
            val end = if (index + 1 < starts.size) minOf(starts[index + 1], LATIN) else LATIN
            for (unit in start until end) latin[unit] = index
        }
    }

    val count: Int get() = starts.size

    fun index(unit: Char): Int {
        val code = unit.code
        if (code < LATIN) return latin[code]
        var lower = 0
        var upper = starts.size
        while (upper - lower > 1) {
            val middle = (lower + upper) ushr 1
            if (starts[middle] <= code) lower = middle else upper = middle
        }
        return lower
    }

    private companion object {
        const val LATIN = 256

        val shared: List<Int> = (boundaries(listOf(CharSet.word, CharSet.lineTerminator)) + 0).sorted()

        fun boundaries(sets: List<CharSet>): MutableList<Int> {
            val boundaries = ArrayList<Int>()
            for (set in sets) {
                for (index in 0 until set.rangeCount) {
                    boundaries.add(set.lowerAt(index))
                    boundaries.add(set.upperAt(index) + 1)
                }
            }
            return boundaries
        }

        fun merged(
            first: List<Int>,
            second: List<Int>,
        ): IntArray {
            val starts = IntList()
            var left = 0
            var right = 0
            var last = -1
            while (left < first.size || right < second.size) {
                val next =
                    if (right == second.size || (left < first.size && first[left] <= second[right])) {
                        first[left++]
                    } else {
                        second[right++]
                    }
                if (next <= CharSet.MAX_UNIT && next != last) {
                    starts.add(next)
                    last = next
                }
            }
            return starts.toArray()
        }
    }
}
