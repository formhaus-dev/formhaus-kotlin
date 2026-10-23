package dev.formhaus.core.engine

import dev.formhaus.core.cldr.BoundedCache
import dev.formhaus.core.cldr.JvmRecallFormatter
import dev.formhaus.core.json.jsCollapsed
import dev.formhaus.core.json.jsNumberString
import dev.formhaus.core.model.FormField
import dev.formhaus.core.validation.matchesFormat
import dev.formhaus.core.validation.numberTypes
import java.time.LocalDateTime
import java.time.format.FormatStyle
import java.util.Locale

public sealed class RecallAnswer {
    public data class Text(
        public val text: String,
    ) : RecallAnswer()

    public data class Number(
        public val number: Double,
    ) : RecallAnswer()

    public data class Items(
        public val items: List<String>,
    ) : RecallAnswer()

    public val stored: String
        get() =
            when (this) {
                is Text -> text
                is Number -> jsNumberString(number)
                is Items -> items.joinToString(", ")
            }

    internal val collapsed: RecallAnswer
        get() =
            when (this) {
                is Text -> Text(text.jsCollapsed)
                is Number -> this
                is Items -> Items(items.map { it.jsCollapsed })
            }
}

public typealias RecallFormatFn = (RecallAnswer, FormField, String) -> String

private val stampTypes = listOf("date", "time", "datetime")
private const val LOCALE_LIMIT = 32
internal const val MAXIMUM_SIGNIFICANT_DIGITS = 21
private val locales = BoundedCache<String, Locale>(LOCALE_LIMIT)
private val languages: Set<String> by lazy { Locale.getAvailableLocales().mapTo(HashSet()) { it.language.lowercase() } }
private val asciiLetters = ('a'..'z') + ('A'..'Z')

private fun isWellFormed(tag: String): Boolean {
    val parts = tag.split('-')
    val language = parts.first()
    if (language.length !in listOf(2, 3, 5, 6, 7, 8) || !language.all { it in asciiLetters }) return false
    return parts.drop(1).all { part -> part.length in 1..8 && part.all { it in asciiLetters || it in '0'..'9' } }
}

private fun supportedLocale(tag: String): Locale {
    if (!isWellFormed(tag) || tag.substringBefore('-').lowercase() !in languages) return Locale.ENGLISH
    return Locale.forLanguageTag(tag)
}

internal fun recallLocale(tag: String): Locale = locales.getOrPut(tag, ::supportedLocale)

private fun digitRuns(value: String): List<Int> {
    val runs = ArrayList<Int>()
    var run = -1
    for (char in value) {
        if (char in '0'..'9') {
            run = maxOf(run, 0) * 10 + (char - '0')
        } else if (run >= 0) {
            runs.add(run)
            run = -1
        }
    }
    if (run >= 0) runs.add(run)
    return runs
}

private fun stamp(
    type: String,
    value: String,
): LocalDateTime? {
    val digits = digitRuns(value)
    val parts = if (type == "time") listOf(1970, 1, 1) + digits else digits
    if (parts.size < 3) return null
    return runCatching {
        LocalDateTime.of(parts[0], parts[1], parts[2], parts.getOrElse(3) { 0 }, parts.getOrElse(4) { 0 })
    }.getOrNull()
}

private fun RecallFormatter.localized(
    answer: RecallAnswer,
    type: String,
    locale: Locale,
): String? =
    when (answer) {
        is RecallAnswer.Items -> {
            conjunction(answer.items, locale)
        }

        is RecallAnswer.Number -> {
            val localizable = type in numberTypes || type == "group"
            if (localizable && 'e' !in jsNumberString(answer.number)) {
                number(answer.number, MAXIMUM_SIGNIFICANT_DIGITS, locale)
            } else {
                null
            }
        }

        is RecallAnswer.Text -> {
            val stamp = if (type in stampTypes && matchesFormat(type, answer.text)) stamp(type, answer.text) else null
            stamp?.let {
                val dateStyle = if (type == "time") null else FormatStyle.MEDIUM
                val timeStyle = if (type == "date") null else FormatStyle.SHORT
                dateTime(it, dateStyle, timeStyle, locale)
            }
        }
    }

internal fun recallFormat(formatter: RecallFormatter): RecallFormatFn =
    { answer, field, locale ->
        val collapsed = answer.collapsed
        formatter.localized(collapsed, field.type, recallLocale(locale)) ?: collapsed.stored
    }

internal val defaultRecallFormat: RecallFormatFn = recallFormat(JvmRecallFormatter)
