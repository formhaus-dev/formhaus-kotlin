package dev.formhaus.compose.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.format.FormatStyle
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class IcuRecallFormatterTest {
    private val date = LocalDateTime.of(2026, 10, 22, 14, 30)

    @Test
    fun listsJoinWithTheLocaleConjunction() {
        assertEquals("Ada, Bo, and Cy", IcuRecallFormatter.conjunction(listOf("Ada", "Bo", "Cy"), Locale.US))
        assertEquals("Ada, Bo und Cy", IcuRecallFormatter.conjunction(listOf("Ada", "Bo", "Cy"), Locale.GERMANY))
    }

    @Test
    fun numbersKeepTheSignificantDigitsInTheLocaleFormat() {
        assertEquals("1,234.5", IcuRecallFormatter.number(1234.5, 15, Locale.US))
        assertEquals("1.234,5", IcuRecallFormatter.number(1234.5, 15, Locale.GERMANY))
        assertEquals("0.333", IcuRecallFormatter.number(1.0 / 3, 3, Locale.US))
    }

    @Test
    fun datesUseTheLocaleCalendarInUtc() {
        assertEquals("Oct 22, 2026", IcuRecallFormatter.dateTime(date, FormatStyle.MEDIUM, null, Locale.US))
        assertTrue(IcuRecallFormatter.dateTime(date, FormatStyle.LONG, null, Locale.forLanguageTag("th-TH-u-ca-buddhist")).contains("2569"))
        assertTrue(IcuRecallFormatter.dateTime(date, FormatStyle.LONG, null, Locale.forLanguageTag("ja-JP-u-ca-japanese")).contains("令和8"))
        assertTrue(IcuRecallFormatter.dateTime(date, null, FormatStyle.SHORT, Locale.US).startsWith("2:30"))
    }
}
