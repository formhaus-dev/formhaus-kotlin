package dev.formhaus.compose.platform

import android.icu.text.DateFormat
import android.icu.text.ListFormatter
import android.icu.text.NumberFormat
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import dev.formhaus.core.engine.RecallFormatter
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

internal object IcuRecallFormatter : RecallFormatter {
    override fun conjunction(
        items: List<String>,
        locale: Locale,
    ): String = ListFormatter.getInstance(locale).format(items)

    override fun number(
        value: Double,
        maximumSignificantDigits: Int,
        locale: Locale,
    ): String {
        val format = NumberFormat.getInstance(locale)
        if (format is android.icu.text.DecimalFormat) {
            format.setSignificantDigitsUsed(true)
            format.minimumSignificantDigits = 1
            format.maximumSignificantDigits = maximumSignificantDigits
        }
        return format.format(value)
    }

    override fun dateTime(
        utc: LocalDateTime,
        dateStyle: FormatStyle?,
        timeStyle: FormatStyle?,
        locale: Locale,
    ): String {
        val ulocale = ULocale.forLocale(locale)
        val calendar = Calendar.getInstance(TimeZone.GMT_ZONE, ulocale)
        val format = DateFormat.getDateTimeInstance(calendar, style(dateStyle), style(timeStyle), ulocale)
        format.timeZone = TimeZone.GMT_ZONE
        return format.format(Date.from(utc.toInstant(ZoneOffset.UTC)))
    }

    private fun style(style: FormatStyle?): Int =
        when (style) {
            null -> DateFormat.NONE
            FormatStyle.FULL -> DateFormat.FULL
            FormatStyle.LONG -> DateFormat.LONG
            FormatStyle.MEDIUM -> DateFormat.MEDIUM
            FormatStyle.SHORT -> DateFormat.SHORT
        }
}
