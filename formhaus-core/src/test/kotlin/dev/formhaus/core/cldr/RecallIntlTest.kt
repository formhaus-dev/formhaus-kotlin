package dev.formhaus.core.cldr

import dev.formhaus.core.engine.RecallAnswer
import dev.formhaus.core.engine.defaultRecallFormat
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.support.field
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecallIntlTest {
    private class Case(
        val locale: String,
        val type: String,
        val value: JsonValue,
        val expected: String,
    )

    private val fixture by lazy {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/cldr/recall-format.json")).use { it.readBytes() }
        requireNotNull(JsonValue.parse(bytes).objectValue)
    }

    private val cases: List<Case> by lazy {
        requireNotNull(fixture["cases"]?.arrayValue).map { item ->
            val case = requireNotNull(item.objectValue)

            fun text(key: String) = requireNotNull(case[key]?.stringValue)
            Case(text("locale"), text("type"), requireNotNull(case["value"]), text("expected"))
        }
    }

    private val olderJdkDatePatterns =
        """
        en-IN date, en-IN datetime, es datetime, es-MX date, es-MX time, es-MX datetime, pt-BR datetime,
        fr datetime, fr-CA datetime, nl date, nl datetime, fi datetime, bn date, bn datetime, ar datetime,
        ar-SA datetime, ar-EG datetime, ar-u-nu-arab datetime, zh time, zh datetime, zh-TW time, ko time,
        ko datetime, vi datetime, id datetime, sr date, sr datetime, sr-Latn date, sr-Latn datetime, my date,
        my time, my datetime, sw datetime, ur datetime, ta time, ta datetime
        """.split(',').map(String::trim)
    private val olderJdkNumberSymbols = setOf("de-CH number", "km number")
    private val missingPersianChronology = setOf("fa-IR date", "fa-IR datetime")
    private val julianBeforeGregorianCutover = setOf("th-TH date", "ja-JP-u-ca-japanese date")

    private fun answer(value: JsonValue): RecallAnswer =
        when (value) {
            is JsonValue.Array -> RecallAnswer.Items(value.items.map { requireNotNull(it.stringValue) })
            is JsonValue.Number -> RecallAnswer.Number(value.value)
            else -> RecallAnswer.Text(requireNotNull(value.stringValue))
        }

    private fun formatted(case: Case): String =
        defaultRecallFormat(answer(case.value), field("""{"key":"a","type":"${case.type}","label":"A"}"""), case.locale)

    @Test
    fun `the jvm default matches Intl except where JDK 17 data differs`() {
        val differing = cases.filter { formatted(it) != it.expected }.mapTo(sortedSetOf()) { "${it.locale} ${it.type}" }
        val known = olderJdkDatePatterns + olderJdkNumberSymbols + missingPersianChronology + julianBeforeGregorianCutover
        assertEquals(known.toSortedSet(), differing)
    }

    @Test
    fun `the fixture and the tables come from the same CLDR release`() {
        assertEquals(CLDR_VERSION, fixture["cldr"]?.stringValue)
    }

    @Test
    fun `lists match Intl for every locale`() {
        val lists = cases.filter { it.type == "multiselect" }
        assertEquals(lists.map { it.expected }, lists.map(::formatted))
    }
}
