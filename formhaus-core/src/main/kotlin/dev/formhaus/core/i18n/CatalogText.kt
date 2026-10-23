package dev.formhaus.core.i18n

import dev.formhaus.core.cldr.cldrNumber
import dev.formhaus.core.cldr.pluralCategory
import dev.formhaus.core.engine.MAXIMUM_SIGNIFICANT_DIGITS
import dev.formhaus.core.engine.recallLocale
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.validation.MessageKey
import dev.formhaus.core.validation.formatText

private val pluralParams = listOf("count", "min", "max")

private fun catalogText(
    locale: String,
    text: LocaleText,
    params: JsonObject,
): String {
    val tag = recallLocale(locale)
    val name = pluralParams.firstOrNull { params[it] is JsonValue.Number }
    val number = name?.let { params[it]?.numberValue }
    val values = params.toMutableStringMap()
    if (name != null && number != null) values[name] = JsonValue.String(cldrNumber(number, MAXIMUM_SIGNIFICANT_DIGITS, tag))
    val chosen =
        when (text) {
            is LocaleText.Plain -> text.text
            is PluralForms -> number?.let { text.form(pluralCategory(tag, it)) } ?: text.other
        }
    return formatText(chosen, values)
}

private fun <K> List<LocaleCatalog>.textOf(
    key: K,
    params: JsonObject,
    texts: (LocaleCatalog) -> Map<K, LocaleText>,
): String? =
    firstNotNullOfOrNull { catalog ->
        texts(catalog)[key]?.let { catalogText(catalog.locale, it, params) }?.takeIf { it.isNotEmpty() }
    }

internal fun List<LocaleCatalog>.messageText(
    key: MessageKey,
    params: JsonObject,
): String? = textOf(key, params, LocaleCatalog::messages)

internal fun List<LocaleCatalog>.labelText(
    key: String,
    params: JsonObject,
): String? = textOf(key, params, LocaleCatalog::labels)
