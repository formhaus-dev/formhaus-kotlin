package dev.formhaus.core.i18n

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.ValueParts
import dev.formhaus.core.json.describe
import dev.formhaus.core.json.equalParts
import dev.formhaus.core.json.isBlankText
import dev.formhaus.core.json.partsHash
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.validation.defaultMessageKeys

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
