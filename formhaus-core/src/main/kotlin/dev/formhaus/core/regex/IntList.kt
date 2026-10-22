package dev.formhaus.core.regex

internal class IntList(
    capacity: Int = 16,
) {
    private var values = IntArray(maxOf(capacity, 4))
    var size: Int = 0
        private set

    operator fun get(index: Int): Int = values[index]

    operator fun set(
        index: Int,
        value: Int,
    ) {
        values[index] = value
    }

    fun add(value: Int) {
        if (size == values.size) values = values.copyOf(2 * values.size)
        values[size++] = value
    }

    fun addRange(
        lower: Int,
        upper: Int,
    ) {
        add(lower)
        add(upper)
    }

    fun removeLast(): Int = values[--size]

    fun truncate(length: Int) {
        size = length
    }

    fun isEmpty(): Boolean = size == 0

    fun toArray(): IntArray = values.copyOf(size)
}
