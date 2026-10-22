package dev.formhaus.core.cldr

import dev.formhaus.core.unicode.isHebrewScript
import java.util.Locale

private const val SPANISH_AND = " y "
private const val SPANISH_AND_BEFORE_I = " e "
private const val HEBREW_AND = " ו"
private const val HEBREW_AND_BEFORE_OTHER_SCRIPT = " ו-"

private class ListPattern(
    val start: String,
    val middle: String,
    val end: String,
    val pair: String,
    val trailing: String,
    val pairTrailing: String,
) {
    fun format(
        items: List<String>,
        language: String,
    ): String {
        if (items.size < 2) return items.firstOrNull().orEmpty()
        if (items.size == 2) return items[0] + contextual(pair, items[1], language) + items[1] + pairTrailing
        val last = items.last()
        return buildString {
            append(items[0], start, items[1])
            for (index in 2 until items.lastIndex) append(middle, items[index])
            append(contextual(end, last, language), last, trailing)
        }
    }
}

private fun contextual(
    separator: String,
    next: String,
    language: String,
): String =
    when {
        language == "es" && separator == SPANISH_AND && startsWithI(next) -> SPANISH_AND_BEFORE_I
        language == "he" && separator == HEBREW_AND && startsOutsideHebrew(next) -> HEBREW_AND_BEFORE_OTHER_SCRIPT
        else -> separator
    }

private fun isI(char: Char?): Boolean = char == 'i' || char == 'I'

private fun startsWithI(text: String): Boolean {
    val first = text.firstOrNull()
    if (isI(first)) return true
    val third = text.getOrNull(2)?.lowercaseChar()
    return (first == 'h' || first == 'H') && isI(text.getOrNull(1)) && third != 'a' && third != 'e'
}

private fun startsOutsideHebrew(text: String): Boolean = text.isNotEmpty() && !isHebrewScript(text.codePointAt(0))

private fun listPattern(encoded: String): ListPattern {
    val fields = encoded.split('|')
    return ListPattern(
        start = fields[0],
        middle = fields[1].ifEmpty { fields[0] },
        end = fields[2],
        pair = fields[3].ifEmpty { fields[2] },
        trailing = fields.getOrElse(4) { "" },
        pairTrailing = fields.getOrElse(5) { "" },
    )
}

private val listPatterns by lazy { CldrTable(LIST_PATTERNS, FALLBACK_LIST_PATTERNS, ::listPattern) }

internal fun cldrConjunction(
    items: List<String>,
    locale: Locale,
): String = listPatterns[locale].format(items, locale.language)
