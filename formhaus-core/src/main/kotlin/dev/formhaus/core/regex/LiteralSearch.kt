package dev.formhaus.core.regex

internal class LiteralSearch(val literal: CharArray) {
    private val shifts = IntArray(if (literal.size > 1) 256 else 0) { literal.size }

    init {
        for (index in 0 until literal.size - 1) shifts[literal[index].code and 0xFF] = literal.size - 1 - index
    }

    fun index(text: CharArray, origin: Int): Int? {
        if (literal.isEmpty()) return origin.takeIf { it <= text.size }
        val last = literal.size - 1
        val tail = literal[last]
        if (last == 0) {
            for (index in origin until text.size) if (text[index] == tail) return index
            return null
        }
        var start = origin
        while (start + last < text.size) {
            val unit = text[start + last]
            if (unit == tail && matchesAt(text, start, last)) return start
            start += shifts[unit.code and 0xFF]
        }
        return null
    }

    private fun matchesAt(text: CharArray, start: Int, length: Int): Boolean {
        for (index in 0 until length) if (text[start + index] != literal[index]) return false
        return true
    }
}
