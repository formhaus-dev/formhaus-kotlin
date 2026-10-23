package dev.formhaus.core.i18n

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.ValueParts
import dev.formhaus.core.json.describe
import dev.formhaus.core.json.equalParts
import dev.formhaus.core.json.partsHash
import dev.formhaus.core.validation.MessageKey
import java.util.concurrent.ConcurrentHashMap

public sealed interface LocaleText {
    public class Plain(
        public val text: String,
    ) : LocaleText {
        override fun equals(other: Any?): Boolean = equalParts(other, Plain::parts)

        override fun hashCode(): Int = parts().partsHash()

        override fun toString(): String = parts().describe("Plain")

        private fun parts(): ValueParts = listOf("text" to text)
    }
}

public class PluralForms(
    public val other: String,
    public val zero: String? = null,
    public val one: String? = null,
    public val two: String? = null,
    public val few: String? = null,
    public val many: String? = null,
) : LocaleText {
    internal fun form(category: String): String? =
        when (category) {
            "zero" -> zero
            "one" -> one
            "two" -> two
            "few" -> few
            "many" -> many
            else -> other
        }

    override fun equals(other: Any?): Boolean = equalParts(other, PluralForms::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("PluralForms")

    private fun parts(): ValueParts = listOf("other" to other, "zero" to zero, "one" to one, "two" to two, "few" to few, "many" to many)
}

internal val pluralCategories = listOf("zero", "one", "two", "few", "many", "other")

private fun localeText(json: JsonValue): LocaleText? {
    json.stringValue?.let { return LocaleText.Plain(it) }
    val forms = json.objectValue ?: return null
    val texts = pluralCategories.associateWith { forms[it]?.stringValue }
    val other = texts["other"] ?: return null
    return PluralForms(other, texts["zero"], texts["one"], texts["two"], texts["few"], texts["many"])
}

private fun <K> texts(
    json: JsonValue?,
    key: (String) -> K,
): Map<K, LocaleText> {
    val block = json?.objectValue ?: return emptyMap()
    val result = LinkedHashMap<K, LocaleText>()
    for ((name, value) in block) localeText(value)?.let { result[key(name)] = it }
    return result
}

public class LocaleCatalog(
    public val locale: String,
    public val messages: Map<MessageKey, LocaleText> = emptyMap(),
    public val labels: Map<String, LocaleText> = emptyMap(),
) {
    override fun equals(other: Any?): Boolean = equalParts(other, LocaleCatalog::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("LocaleCatalog")

    private fun parts(): ValueParts = listOf("locale" to locale, "messages" to messages, "labels" to labels)

    public companion object {
        public val bundledTags: List<String> =
            listOf("de", "en", "es", "fr", "fr-CA", "it", "ja", "pt-BR", "ru", "uk", "zh-CN")

        private val loaded = ConcurrentHashMap<String, LocaleCatalog>()

        public fun fromJson(json: JsonValue): LocaleCatalog? {
            val root = json.objectValue ?: return null
            val locale = root["locale"]?.stringValue ?: return null
            return LocaleCatalog(locale, texts(root["messages"], ::MessageKey), texts(root["labels"]) { it })
        }

        public fun bundled(tag: String): LocaleCatalog? {
            if (tag !in bundledTags) return null
            return loaded.getOrPut(tag) {
                val path = "/dev/formhaus/core/locales/$tag.json"
                val bytes = requireNotNull(LocaleCatalog::class.java.getResourceAsStream(path)).use { it.readBytes() }
                requireNotNull(fromJson(JsonValue.parse(bytes)))
            }
        }
    }
}
