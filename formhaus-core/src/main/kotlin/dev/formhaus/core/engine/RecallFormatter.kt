package dev.formhaus.core.engine

import java.time.LocalDateTime
import java.time.format.FormatStyle
import java.util.Locale

public interface RecallFormatter {
    public fun conjunction(
        items: List<String>,
        locale: Locale,
    ): String

    public fun number(
        value: Double,
        maximumSignificantDigits: Int,
        locale: Locale,
    ): String

    public fun dateTime(
        utc: LocalDateTime,
        dateStyle: FormatStyle?,
        timeStyle: FormatStyle?,
        locale: Locale,
    ): String
}
