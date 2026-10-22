package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.json.quoteJson

private const val SHOWN_CHOICES = 10
private const val SHORT_NAME = 4

internal fun oneOf(values: List<String>): String {
    if (values.size == 1) return "Use ${quoteJson(values[0])}"
    val shown = values.take(SHOWN_CHOICES).joinToString(", ", transform = ::quoteJson)
    return if (values.size > SHOWN_CHOICES) "Use one of: $shown, ..." else "Use one of: $shown"
}

private fun distance(a: String, b: String): Int {
    var row = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val next = IntArray(b.length + 1)
        next[0] = i
        for (j in 1..b.length) {
            next[j] = minOf(row[j] + 1, next[j - 1] + 1, row[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        row = next
    }
    return row[b.length]
}

internal fun closest(value: String, candidates: List<String>): String? {
    val lower = value.lowercase()
    return candidates.firstOrNull { candidate ->
        candidate != value && distance(lower, candidate.lowercase()) <= if (candidate.length > SHORT_NAME) 2 else 1
    }
}

internal fun suggest(value: String, candidates: List<String>): String? {
    closest(value, candidates)?.let { return "Use ${quoteJson(it)}" }
    return if (candidates.isEmpty()) null else oneOf(candidates)
}
