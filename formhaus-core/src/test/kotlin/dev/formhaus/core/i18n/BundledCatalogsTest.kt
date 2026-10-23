package dev.formhaus.core.i18n

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.validation.defaultMessages
import dev.formhaus.core.validation.replacePlaceholders
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class BundledCatalogsTest {
    private val english = defaultMessages.mapKeys { it.key.rawValue } + defaultLabels
    private val categories =
        mapOf(
            "en de" to "one other",
            "es fr fr-CA it pt-BR" to "one many other",
            "ja zh-CN" to "other",
            "ru uk" to "one few many other",
        ).flatMap { (tags, forms) -> tags.split(' ').map { it to forms.split(' ') } }.toMap()

    private fun placeholderNames(text: String): List<String> {
        val names = ArrayList<String>()
        replacePlaceholders(text) { name ->
            names.add(name)
            null
        }
        return names
    }

    private fun json(tag: String): JsonValue {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/dev/formhaus/core/locales/$tag.json")).use { it.readBytes() }
        return JsonValue.parse(bytes)
    }

    private fun texts(catalog: LocaleCatalog): List<Pair<String, LocaleText>> =
        catalog.messages.mapKeys { it.key.rawValue }.toList() + catalog.labels.toList()

    private fun forms(text: LocaleText): List<String> =
        when (text) {
            is LocaleText.Plain -> listOf(text.text)
            is PluralForms -> listOfNotNull(text.zero, text.one, text.two, text.few, text.many, text.other)
        }

    @Test
    fun `ships the first set of languages`() {
        assertEquals(categories.keys.sorted(), LocaleCatalog.bundledTags.sorted())
    }

    @Test
    fun `keeps the English catalog equal to the built-in texts`() {
        val catalog = requireNotNull(LocaleCatalog.bundled("en"))
        assertEquals("en", catalog.locale)
        assertEquals(english, texts(catalog).associate { (key, text) -> key to (text as LocaleText.Plain).text })
    }

    @Test
    fun `loads only bundled tags`() {
        assertNull(LocaleCatalog.bundled("../VERSION"))
        assertNull(LocaleCatalog.bundled("pl"))
        assertEquals(LocaleCatalog.bundled("ru"), LocaleCatalog.fromJson(json("ru")))
    }

    @Test
    fun `loads the catalogs of the fallback chain`() {
        fun locales(tag: String) = localeChain(tag).filter { it != "en" }.mapNotNull(LocaleCatalog::bundled).map { it.locale }
        assertEquals(listOf("fr-CA", "fr"), locales("fr-CA"))
        assertEquals(listOf("ru"), locales("ru-RU"))
        assertEquals(listOf("zh-CN"), locales("zh-Hans-CN"))
        assertEquals(listOf("pt-BR"), locales("pt"))
        assertEquals(emptyList<String>(), locales("en-GB"))
    }

    @TestFactory
    fun `every catalog is complete and keeps the English placeholders`(): List<DynamicTest> =
        LocaleCatalog.bundledTags.map { tag ->
            DynamicTest.dynamicTest(tag) {
                val catalog = requireNotNull(LocaleCatalog.bundled(tag))
                assertEquals(tag, catalog.locale)
                assertEquals(CatalogIssues(emptyList(), emptyList()), checkCatalog(json(tag)))
                for ((key, text) in texts(catalog)) {
                    val expected = placeholderNames(english.getValue(key)).sorted()
                    for (form in forms(text)) {
                        assertEquals(expected, placeholderNames(form).sorted(), "$key: $form")
                        assertEquals(form.trim(), form, key)
                    }
                    if (text is PluralForms) {
                        val present =
                            listOf(
                                "zero" to text.zero,
                                "one" to text.one,
                                "two" to text.two,
                                "few" to text.few,
                                "many" to text.many,
                            )
                        assertEquals(categories.getValue(tag), present.filter { it.second != null }.map { it.first } + "other", key)
                    }
                }
            }
        }
}
