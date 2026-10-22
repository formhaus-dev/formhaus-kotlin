package dev.formhaus.core.cldr

import dev.formhaus.core.engine.RecallFormatter
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.chrono.Chronology
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale

private const val CACHE_LIMIT = 32

private data class DateKey(
    val locale: Locale,
    val dateStyle: FormatStyle?,
    val timeStyle: FormatStyle?,
)

private fun chronology(locale: Locale): Chronology =
    if (locale.getUnicodeLocaleType("ca") == null && calendars[locale] == FALLBACK_CALENDARS) {
        IsoChronology.INSTANCE
    } else {
        runCatching { Chronology.ofLocale(locale.withDefault("ca", calendars[locale])) }
            .getOrDefault(IsoChronology.INSTANCE)
    }

private fun dateFormatter(key: DateKey): DateTimeFormatter {
    val locale = key.locale.withDefault("nu", numberingSystems[key.locale])
    return DateTimeFormatterBuilder()
        .appendLocalized(key.dateStyle, key.timeStyle)
        .toFormatter(locale)
        .withChronology(chronology(key.locale))
        .withDecimalStyle(DecimalStyle.of(locale))
}

private val dateFormatters = BoundedCache<DateKey, DateTimeFormatter>(CACHE_LIMIT)

internal object JvmRecallFormatter : RecallFormatter {
    override fun conjunction(
        items: List<String>,
        locale: Locale,
    ): String = cldrConjunction(items, locale)

    override fun number(
        value: Double,
        maximumSignificantDigits: Int,
        locale: Locale,
    ): String = cldrNumber(value, maximumSignificantDigits, locale)

    override fun dateTime(
        utc: LocalDateTime,
        dateStyle: FormatStyle?,
        timeStyle: FormatStyle?,
        locale: Locale,
    ): String {
        val formatter = dateFormatters.getOrPut(DateKey(locale, dateStyle, timeStyle), ::dateFormatter)
        return try {
            formatter.format(utc)
        } catch (_: DateTimeException) {
            formatter.withChronology(IsoChronology.INSTANCE).format(utc)
        }
    }
}
