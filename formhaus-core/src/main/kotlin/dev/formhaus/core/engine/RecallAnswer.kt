package dev.formhaus.core.engine

import dev.formhaus.core.json.jsCollapsed
import dev.formhaus.core.json.jsNumberString
import dev.formhaus.core.model.FormField
import dev.formhaus.core.validation.matchesFormat
import dev.formhaus.core.validation.numberTypes
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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
private val locales = ConcurrentHashMap<String, Locale>()
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

internal fun recallLocale(tag: String): Locale {
    locales[tag]?.let { return it }
    if (locales.size >= LOCALE_LIMIT) locales.clear()
    return supportedLocale(tag).also { locales[tag] = it }
}

private fun stamp(
    type: String,
    value: String,
): LocalDateTime? {
    val digits = value.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
    val parts = if (type == "time") listOf(1970, 1, 1) + digits else digits
    if (parts.size < 3) return null
    return runCatching {
        LocalDateTime.of(parts[0], parts[1], parts[2], parts.getOrElse(3) { 0 }, parts.getOrElse(4) { 0 })
    }.getOrNull()
}

private fun dateFormatter(
    type: String,
    locale: Locale,
): DateTimeFormatter =
    when (type) {
        "date" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        "time" -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        else -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    }.withLocale(locale)

private fun andList(
    items: List<String>,
    locale: Locale,
): String {
    if (locale.language != "en" || items.size < 2) return items.joinToString(", ")
    if (items.size == 2) return "${items[0]} and ${items[1]}"
    return items.dropLast(1).joinToString(", ") + ", and " + items.last()
}

private fun numberText(
    number: Double,
    locale: Locale,
): String {
    val format = NumberFormat.getNumberInstance(locale) as? DecimalFormat ?: return jsNumberString(number)
    format.maximumFractionDigits = Int.MAX_VALUE
    return format.format(BigDecimal(jsNumberString(number + 0.0)))
}

private fun localized(
    answer: RecallAnswer,
    type: String,
    locale: Locale,
): String? =
    when (answer) {
        is RecallAnswer.Items -> {
            andList(answer.items, locale)
        }

        is RecallAnswer.Number -> {
            if ((type in numberTypes || type == "group") &&
                'e' !in jsNumberString(answer.number)
            ) {
                numberText(answer.number, locale)
            } else {
                null
            }
        }

        is RecallAnswer.Text -> {
            if (type in stampTypes &&
                matchesFormat(type, answer.text)
            ) {
                stamp(type, answer.text)?.format(dateFormatter(type, locale))
            } else {
                null
            }
        }
    }

internal val defaultRecallFormat: RecallFormatFn = { answer, field, locale ->
    val collapsed = answer.collapsed
    localized(collapsed, field.type, recallLocale(locale)) ?: collapsed.stored
}
