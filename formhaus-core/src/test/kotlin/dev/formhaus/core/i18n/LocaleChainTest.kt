package dev.formhaus.core.i18n

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.validation.defaultMessages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class LocaleChainTest {
    private val chains =
        listOf(
            "ja-JP" to "ja-JP ja en",
            "zh-hant-tw" to "zh-Hant-TW zh-Hant zh-TW zh en",
            "zh-Hans-CN" to "zh-Hans-CN zh-Hans zh-CN zh en",
            "zh-Hant" to "zh-Hant zh-TW zh en",
            "zh" to "zh zh-CN en",
            "pt" to "pt pt-BR en",
            "pt_br" to "pt-BR pt en",
            "en-US" to "en-US en",
            "en" to "en-US en",
            "en-US-u-va-posix" to "en-US en",
            "de-CH-u-co-phonebk" to "de-CH de en",
            "fr-CA" to "fr-CA fr en",
            "" to "en",
            "not a tag" to "en",
        )

    @TestFactory
    fun `lists the tags to try`(): List<DynamicTest> =
        chains.map { (tag, chain) -> DynamicTest.dynamicTest("'$tag'") { assertEquals(chain.split(' '), localeChain(tag)) } }

    private fun english(): MutableJsonObject {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/dev/formhaus/core/locales/en.json")).use { it.readBytes() }
        return requireNotNull(JsonValue.parse(bytes).objectValue).toMutableStringMap()
    }

    private fun MutableJsonObject.part(name: String): MutableJsonObject =
        requireNotNull(this[name]?.objectValue).toMutableStringMap().also { this[name] = JsonValue.Object(it) }

    @Test
    fun `lists missing and unknown keys`() {
        val catalog = english()
        catalog["region"] = JsonValue.String("RU")
        val messages = catalog.part("messages")
        messages["minLength"] = JsonValue.parse("""{"one":"x","several":"y"}""")
        messages["typo"] = JsonValue.String("z")
        catalog.part("labels")["back"] = JsonValue.String(" ")
        val issues = checkCatalog(JsonValue.Object(catalog))
        assertEquals(listOf("messages.minLength.other", "labels.back"), issues.missing)
        assertEquals(listOf("region", "messages.minLength.several", "messages.typo"), issues.unknown)
    }

    @Test
    fun `accepts a complete catalog and reports everything for a non-object`() {
        assertEquals(CatalogIssues(emptyList(), emptyList()), checkCatalog(JsonValue.Object(english())))
        val keys = 1 + defaultLabels.size + defaultMessages.size
        assertEquals(keys, checkCatalog(JsonValue.Null).missing.size)
        assertEquals(keys, checkCatalog(null).missing.size)
    }
}
