package dev.formhaus.core.i18n

import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.renderer.LabelSource
import dev.formhaus.core.renderer.actionsState
import dev.formhaus.core.renderer.errorSummary
import dev.formhaus.core.renderer.label
import dev.formhaus.core.support.engine
import dev.formhaus.core.validation.MessageKey
import dev.formhaus.core.validation.MessageSource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocaleCatalogTest {
    private fun characters(
        one: String,
        few: String,
        other: String,
    ) = PluralForms(other, one = one, few = few, many = few)

    private val ru =
        LocaleCatalog(
            "ru",
            mapOf(
                MessageKey.REQUIRED to LocaleText.Plain("Обязательное поле"),
                MessageKey.MIN_LENGTH to characters("Не менее {min} символа", "Не менее {min} символов", "Не менее {min} символа"),
            ),
            mapOf(
                "continue" to LocaleText.Plain("Далее"),
                "stepTitle" to LocaleText.Plain("Шаг {index}"),
                "stepErrors" to PluralForms("{count} ошибки", one = "{count} ошибка", few = "{count} ошибки", many = "{count} ошибок"),
            ),
        )

    private fun form(
        minLength: Int,
        extra: String = "",
    ) = """{"id":"f","title":"F","submit":{"label":"Send"}$extra,
        "fields":[{"key":"name","type":"text","label":"Name","validation":{"required":true,"minLength":$minLength}}]}"""

    private fun minLengthError(
        minLength: Int,
        locale: List<LocaleCatalog>,
        messages: MessageSource? = null,
        extra: String = "",
    ): String? =
        engine(form(minLength, extra), """{"name":"a"}""", FormEngineOptions(messages = messages, locale = locale)).validate()["name"]

    private fun catalog(
        locale: String,
        key: MessageKey,
        text: LocaleText,
    ) = LocaleCatalog(locale, mapOf(key to text))

    @Test
    fun `picks the Russian plural form by the min param`() {
        assertEquals("Не менее 3 символов", minLengthError(3, listOf(ru)))
        assertEquals("Не менее 5 символов", minLengthError(5, listOf(ru)))
        assertEquals("Не менее 21 символа", minLengthError(21, listOf(ru)))
        assertEquals("Обязательное поле", engine(form(2), options = FormEngineOptions(locale = listOf(ru))).validate()["name"])
    }

    @Test
    fun `picks the French one, many and other forms`() {
        val fr =
            catalog(
                "fr",
                MessageKey.MIN_LENGTH,
                PluralForms("Au moins {min} caractères", one = "Au moins {min} caractère", many = "Au moins {min} de caractères"),
            )
        val maxLength =
            catalog("fr", MessageKey.MAX_LENGTH, PluralForms("Au plus {max} caractères", one = "Au plus {max} caractère"))
        val tooLong =
            engine(
                """{"id":"f","submit":{"label":"S"},"fields":[{"key":"name","type":"text","label":"Name","validation":{"maxLength":1}}]}""",
                """{"name":"ab"}""",
                FormEngineOptions(locale = listOf(maxLength)),
            )
        assertEquals("Au plus 1 caractère", tooLong.validate()["name"])
        assertEquals("Au moins 2 caractères", minLengthError(2, listOf(fr)))
        assertEquals("Au moins 1 000 000 de caractères", minLengthError(1_000_000, listOf(fr)))
    }

    @Test
    fun `uses the other form in Japanese and formats numbers for the locale`() {
        val ja = catalog("ja", MessageKey.MIN_LENGTH, PluralForms("{min}文字以上で入力してください"))
        assertEquals("2文字以上で入力してください", minLengthError(2, listOf(ja)))
        val de = LocaleText.Plain("Mindestens {min} Zeichen")
        assertEquals("Mindestens 12.000 Zeichen", minLengthError(12_000, listOf(catalog("de", MessageKey.MIN_LENGTH, de))))
        assertEquals("Mindestens 12,000 Zeichen", minLengthError(12_000, listOf(catalog("not a tag", MessageKey.MIN_LENGTH, de))))
    }

    @Test
    fun `takes the first catalog that has the key, then the built-in English`() {
        val regional = catalog("fr-CA", MessageKey.REQUIRED, LocaleText.Plain("Champ requis"))
        val fr =
            LocaleCatalog(
                "fr",
                mapOf(MessageKey.REQUIRED to LocaleText.Plain("Obligatoire"), MessageKey.MIN_LENGTH to LocaleText.Plain("Au moins {min}")),
            )
        assertEquals("Champ requis", engine(form(2), options = FormEngineOptions(locale = listOf(regional, fr))).validate()["name"])
        assertEquals("Au moins 4", minLengthError(4, listOf(regional, fr)))
        assertEquals("Minimum length is 4", minLengthError(4, emptyList()))
    }

    @Test
    fun `ranks the messages option over definition messages over the catalog`() {
        val definitionMessages = ""","messages":{"minLength":"Definition {min}"}"""
        assertEquals("Definition 4", minLengthError(4, listOf(ru), extra = definitionMessages))
        val option = MessageSource.Catalog(JsonValue.parse("""{"minLength":"Option {min}"}"""))
        assertEquals("Option 4", minLengthError(4, listOf(ru), option, definitionMessages))
    }

    @Test
    fun `resolves labels with the same precedence`(): Unit =
        runBlocking {
            val steps =
                """{"id":"f","title":"F","submit":{"label":"Send"},"steps":[
                {"id":"one","title":"","fields":[{"key":"name","type":"text","label":"Name","validation":{"required":true}}]},
                {"id":"two","title":"Two","fields":[{"key":"note","type":"text","label":"Note"}]}]}"""
            val engine = engine(steps, options = FormEngineOptions(locale = listOf(ru)))

            fun count(value: Int) = stringMapOf<JsonValue>("count" to JsonValue.Number(value.toDouble()))
            assertEquals("3 ошибки", engine.label("stepErrors", null, count(3)))
            assertEquals("5 ошибок", engine.label("stepErrors", null, count(5)))
            assertEquals("Prop", engine.label("continue", LabelSource.Catalog(JsonValue.parse("""{"continue":"Prop"}"""))))
            assertEquals("Back", engine.label("back", null))
            assertEquals("Далее", engine.actionsState(null).primaryLabel)
            engine.next()
            assertEquals("Шаг 1", engine.errorSummary().groups[0].title)
        }

    @Test
    fun `ignores catalogs without a matching text`() {
        val broken = LocaleCatalog.fromJson(JsonValue.parse("""{"locale":"ru","messages":{"minLength":{"one":"x"}},"labels":{}}"""))
        assertEquals("Не менее 4 символов", minLengthError(4, listOfNotNull(broken, ru)))
    }

    @Test
    fun `reads a catalog from JSON`() {
        val json =
            """{"locale":"ru","labels":{"back":2},
            "messages":{"required":"Да","minLength":{"one":"a","few":"b","other":"c","many":1}}}"""
        val catalog = LocaleCatalog.fromJson(JsonValue.parse(json))
        val expected =
            LocaleCatalog(
                "ru",
                mapOf(MessageKey.REQUIRED to LocaleText.Plain("Да"), MessageKey.MIN_LENGTH to PluralForms("c", one = "a", few = "b")),
            )
        assertEquals(expected, catalog)
        assertEquals(null, LocaleCatalog.fromJson(JsonValue.parse("""{"messages":{}}""")))
        assertEquals(null, LocaleCatalog.fromJson(JsonValue.parse("[]")))
    }
}
