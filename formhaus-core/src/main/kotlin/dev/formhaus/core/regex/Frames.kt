package dev.formhaus.core.regex

internal class Frames {
    class Scan(
        val pc: Int,
        val position: Int,
        val stop: Int,
        val step: Int,
    )

    private val values = IntList(STRIDE * 64)

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

    fun push(
        kind: Int,
        scan: Scan,
    ) {
        push(kind, scan.pc, scan.position, scan.stop, scan.step)
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

    fun scan(frame: Int): Scan = Scan(values[frame + 1], values[frame + 2], values[frame + 3], values[frame + 4])

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
