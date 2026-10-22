package dev.formhaus.compose.fields

import android.icu.text.DateTimePatternGenerator
import android.icu.util.ULocale
import dev.formhaus.compose.platform.IcuRecallFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.ResolverStyle
import java.util.Locale

private const val TIME_LENGTH = 5
private const val DATETIME_LENGTH = 16

internal enum class DateKind(
    pattern: String,
) {
    DATE("uuuu-MM-dd"),
    TIME("HH:mm"),
    DATETIME("uuuu-MM-dd'T'HH:mm"),
    ;

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

    fun date(text: String): LocalDateTime? {
        val trimmed =
            when (this) {
                DATETIME -> text.take(DATETIME_LENGTH)
                TIME -> text.take(TIME_LENGTH)
                DATE -> text
            }
        return runCatching {
            when (this) {
                DATE -> LocalDate.parse(trimmed, formatter).atStartOfDay()
                TIME -> LocalTime.parse(trimmed, formatter).atDate(LocalDate.of(2000, 1, 1))
                DATETIME -> LocalDateTime.parse(trimmed, formatter)
            }
        }.getOrNull()
    }

    fun text(date: LocalDateTime): String = formatter.format(date)

    fun display(
        text: String,
        locale: Locale,
    ): String? {
        if (text.isEmpty()) return null
        val date = date(text) ?: return null
        return IcuRecallFormatter.dateTime(
            date,
            if (this == TIME) null else FormatStyle.LONG,
            if (this == DATE) null else FormatStyle.SHORT,
            locale,
        )
    }

    fun pattern(locale: Locale): String {
        val date = DateTimePatternGenerator.getInstance(ULocale.forLocale(locale)).getBestPattern("yyyyMMdd").lowercase(locale)
        return when (this) {
            DATE -> date
            TIME -> "--:--"
            DATETIME -> "$date, --:--"
        }
    }

    companion object {
        fun of(type: String): DateKind =
            when (type) {
                "time" -> TIME
                "datetime" -> DATETIME
                else -> DATE
            }
    }
}
