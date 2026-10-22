package dev.formhaus.core.unicode

private const val CAPITAL_DOTTED_I = 0x130
private const val CAPITAL_SIGMA = 0x3A3
private const val FINAL_SIGMA = 0x3C2
private const val COMBINING_DOT_ABOVE = '̇'

private val lowercaseRuns by lazy { CaseRuns(LOWERCASE_RUNS) }
private val cased by lazy { RangeTable(CASED_RANGES) }
private val caseIgnorable by lazy { RangeTable(CASE_IGNORABLE_RANGES) }

private fun isAsciiLower(text: String): Boolean = text.none { it in 'A'..'Z' || it.code >= 0x80 }

private fun casedNear(
    text: String,
    from: Int,
    forward: Boolean,
): Boolean {
    var index = from
    while (if (forward) index < text.length else index > 0) {
        val point = if (forward) text.codePointAt(index) else text.codePointBefore(index)
        if (point !in caseIgnorable) return point in cased
        index += if (forward) Character.charCount(point) else -Character.charCount(point)
    }
    return false
}

private fun isFinalSigma(
    text: String,
    index: Int,
): Boolean = casedNear(text, index, forward = false) && !casedNear(text, index + 1, forward = true)

internal fun jsLowercase(text: String): String {
    if (isAsciiLower(text)) return text
    val result = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        val point = text.codePointAt(index)
        when {
            point == CAPITAL_DOTTED_I -> result.append('i').append(COMBINING_DOT_ABOVE)
            point == CAPITAL_SIGMA && isFinalSigma(text, index) -> result.appendCodePoint(FINAL_SIGMA)
            else -> result.appendCodePoint(lowercaseRuns.map(point))
        }
        index += Character.charCount(point)
    }
    return result.toString()
}
