package dev.formhaus.core.unicode

private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_"
private const val CONTINUES = 32
private const val DIGIT_BITS = 5

private val digits = IntArray(128).also { table -> ALPHABET.forEachIndexed { index, char -> table[char.code] = index } }

internal fun decodeNumbers(text: String): IntArray {
    val numbers = IntArray(text.count { digits[it.code] < CONTINUES })
    var count = 0
    var value = 0
    var shift = 0
    for (char in text) {
        val digit = digits[char.code]
        value = value or ((digit and (CONTINUES - 1)) shl shift)
        if (digit < CONTINUES) {
            numbers[count++] = value
            value = 0
            shift = 0
        } else {
            shift += DIGIT_BITS
        }
    }
    return numbers
}

internal fun lastAtOrBefore(
    sorted: IntArray,
    value: Int,
): Int {
    var lower = 0
    var upper = sorted.size
    while (lower < upper) {
        val middle = (lower + upper) ushr 1
        if (sorted[middle] <= value) lower = middle + 1 else upper = middle
    }
    return lower - 1
}
