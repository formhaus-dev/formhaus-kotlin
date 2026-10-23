package dev.formhaus.core.i18n

import dev.formhaus.core.cldr.likelyRegions
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.ValueParts
import dev.formhaus.core.json.describe
import dev.formhaus.core.json.equalParts
import dev.formhaus.core.json.isBlankText
import dev.formhaus.core.json.partsHash
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.validation.defaultMessageKeys
import java.util.IllformedLocaleException
import java.util.Locale

private fun canonicalTag(tag: String): String? {
    val subtags = tag.replace('_', '-').split('-')
    val cut = subtags.withIndex().indexOfFirst { (index, subtag) -> index > 0 && subtag.length == 1 }
    val head = if (cut < 0) subtags else subtags.subList(0, cut)
    if (head.first().length < 2) return null
    return try {
        Locale
            .Builder()
            .setLanguageTag(head.joinToString("-"))
            .build()
            .toLanguageTag()
    } catch (_: IllformedLocaleException) {
        null
    }
}

private fun likelyTag(tag: String): String? {
    val locale = Locale.forLanguageTag(tag)
    if (locale.country.isNotEmpty()) return "${locale.language}-${locale.country}"
    return likelyRegions[locale].takeIf { it.isNotEmpty() }
}

public fun localeChain(tag: String): List<String> {
    val chain = ArrayList<String>()
    canonicalTag(tag)?.let { canonical ->
        val parts = canonical.split('-')
        for (size in parts.size downTo 1) chain.add(parts.subList(0, size).joinToString("-"))
        likelyTag(canonical)?.let { chain.add(maxOf(chain.size - 1, 1), it) }
    }
    return (chain.filter { it != "en" } + "en").distinct()
}

public class CatalogIssues internal constructor(
    public val missing: List<String>,
    public val unknown: List<String>,
) {
    override fun equals(other: Any?): Boolean = equalParts(other, CatalogIssues::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("CatalogIssues")

    private fun parts(): ValueParts = listOf("missing" to missing, "unknown" to unknown)
}

private val catalogParts = listOf("locale", "messages", "labels")
private val JsonValue?.isMissingText: Boolean get() = this?.stringValue?.isBlankText != false

public fun checkCatalog(catalog: JsonValue?): CatalogIssues {
    val missing = ArrayList<String>()
    val unknown = ArrayList<String>()
    val root = catalog?.objectValue ?: stringMapOf()
    if (root["locale"].isMissingText) missing.add("locale")
    unknown += root.keys.filter { it !in catalogParts }
    for ((part, known) in listOf("messages" to defaultMessageKeys.map { it.rawValue }, "labels" to defaultLabelKeys)) {
        val block = root[part]?.objectValue ?: stringMapOf()
        for (key in known) {
            val text = block[key]
            val forms = text?.objectValue
            if (forms == null) {
                if (text.isMissingText) missing.add("$part.$key")
                continue
            }
            if (forms["other"].isMissingText) missing.add("$part.$key.other")
            unknown +=
                forms.filter { (form, value) -> form !in pluralCategories || value.stringValue == null }.map { "$part.$key.${it.first}" }
        }
        unknown += block.keys.filter { it !in known }.map { "$part.$it" }
    }
    return CatalogIssues(missing, unknown)
}
