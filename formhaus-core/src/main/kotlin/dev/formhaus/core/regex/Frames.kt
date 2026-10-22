package dev.formhaus.core.regex

internal class Frames {
    private val values = IntList(STRIDE * 8)

    val size: Int get() = values.size

    fun push(
        kind: Int,
        a: Int,
        b: Int = 0,
        c: Int = 0,
        d: Int = 0,
    ) {
        values.add(kind)
        values.add(a)
        values.add(b)
        values.add(c)
        values.add(d)
    }

    fun pop(): Int {
        values.truncate(values.size - STRIDE)
        return values.size
    }

    fun truncate(size: Int) {
        values.truncate(size)
    }

    fun dropLast() {
        values.truncate(values.size - STRIDE)
    }

    fun kind(frame: Int): Int = values[frame]

    fun a(frame: Int): Int = values[frame + 1]

    fun b(frame: Int): Int = values[frame + 2]

    fun c(frame: Int): Int = values[frame + 3]

    fun d(frame: Int): Int = values[frame + 4]

    companion object {
        const val RESUME: Int = 0
        const val REGISTER: Int = 1
        const val CAPTURE: Int = 2
        const val CAPTURES: Int = 3
        const val SCAN: Int = 4
        const val EXTEND: Int = 5
        private const val STRIDE = 5
    }
}
