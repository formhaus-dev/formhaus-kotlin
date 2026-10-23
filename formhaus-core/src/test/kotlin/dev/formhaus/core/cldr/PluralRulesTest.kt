package dev.formhaus.core.cldr

import dev.formhaus.core.engine.recallLocale
import dev.formhaus.core.json.JsonValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PluralRulesTest {
    private val fixture by lazy {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/cldr/plural-rules.json")).use { it.readBytes() }
        requireNotNull(JsonValue.parse(bytes).objectValue)
    }

    @Test
    fun `plural categories match Intl PluralRules for the catalog languages`() {
        val cases = requireNotNull(fixture["cases"]?.arrayValue).map { requireNotNull(it.objectValue) }
        val expected = cases.map { "${it["locale"]?.stringValue} ${it["value"]?.numberValue} ${it["expected"]?.stringValue}" }
        val actual =
            cases.map { case ->
                val locale = requireNotNull(case["locale"]?.stringValue)
                val value = requireNotNull(case["value"]?.numberValue)
                "$locale $value ${pluralCategory(recallLocale(locale), value)}"
            }
        assertEquals(expected, actual)
        assertEquals(CLDR_VERSION, fixture["cldr"]?.stringValue)
    }
}
