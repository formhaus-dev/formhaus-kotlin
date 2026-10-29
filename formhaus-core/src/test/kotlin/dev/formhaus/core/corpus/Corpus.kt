package dev.formhaus.core.corpus

import dev.formhaus.core.engine.HiddenValues
import dev.formhaus.core.engine.RecallItem
import dev.formhaus.core.i18n.LocaleCatalog
import dev.formhaus.core.i18n.localize
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.support.Fixtures

class CorpusCase(
    json: JsonValue,
) {
    val name: String = json["name"]?.stringValue.orEmpty()
    val values: JsonValue = json["input"]?.get("values") ?: JsonValue.Object(MutableJsonObject())
    val skippedSteps: JsonValue = json["input"]?.get("skippedSteps") ?: JsonValue.Array(emptyList())
    val hiddenValues: HiddenValues =
        HiddenValues.of(json["input"]?.get("hiddenValues")?.stringValue) ?: HiddenValues.CLEAR
    val expectedValues: JsonValue = json["expected"]?.get("values") ?: JsonValue.Object(MutableJsonObject())
    val expectedErrors: JsonValue = json["expected"]?.get("errors") ?: JsonValue.Object(MutableJsonObject())
    val canSubmit: Boolean = json["canSubmit"]?.boolValue ?: false
    val serverValues: JsonValue? = json["serverValues"]
}

class RecallEntry(
    val address: String,
    val item: RecallItem?,
    val text: String,
)

class RecallCase(
    json: JsonValue,
) {
    val name: String = json["name"]?.stringValue.orEmpty()
    val values: JsonObject = json["values"]?.objectValue ?: MutableJsonObject()
    val texts: List<RecallEntry> =
        json["texts"]?.arrayValue.orEmpty().map { entry ->
            val item = entry["group"]?.stringValue?.let { RecallItem(it, (entry["item"]?.numberValue ?: 0.0).toInt()) }
            RecallEntry(entry["address"]?.stringValue.orEmpty(), item, entry["text"]?.stringValue.orEmpty())
        }
}

data class IssueEntry(
    val code: String,
    val path: String,
) : Comparable<IssueEntry> {
    override fun compareTo(other: IssueEntry): Int = compareValuesBy(this, other, { it.path }, { it.code })

    override fun toString(): String = "$path $code"

    companion object {
        fun list(value: JsonValue?): List<IssueEntry>? =
            value?.arrayValue?.map { IssueEntry(it["code"]?.stringValue.orEmpty(), it["path"]?.stringValue.orEmpty()) }
    }
}

class CorpusFixture(
    val name: String,
    json: JsonValue,
) {
    val issues: List<IssueEntry> = IssueEntry.list(json["issues"]).orEmpty()
    val warnings: List<IssueEntry>? = IssueEntry.list(json["warnings"])
    val refused: List<IssueEntry>? = IssueEntry.list(json["refused"])
    val definition: JsonValue = json["definition"] ?: JsonValue.Null
    private val overlay: JsonValue? = json["overlay"]
    val locale: List<LocaleCatalog> = json["locale"]?.arrayValue.orEmpty().mapNotNull(LocaleCatalog::fromJson)
    val cases: List<CorpusCase> = json["cases"]?.arrayValue.orEmpty().map(::CorpusCase)
    val recall: List<RecallCase> = json["recall"]?.arrayValue.orEmpty().map(::RecallCase)
    val reveals: JsonValue? = json["reveals"]

    fun runtime(): FormDefinition = overlay?.let { localize(definition, it).definition } ?: FormDefinition(definition)

    companion object {
        val all: List<CorpusFixture> by lazy {
            Fixtures.conformanceNames.map { CorpusFixture(it, Fixtures.conformanceFile(it)) }
        }

        val valid: List<CorpusFixture> by lazy { all.filter { it.issues.isEmpty() } }
    }
}
