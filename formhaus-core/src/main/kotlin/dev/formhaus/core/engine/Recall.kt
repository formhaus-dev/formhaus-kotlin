package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.isBlankText
import dev.formhaus.core.json.jsString
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.validation.TemplateToken
import dev.formhaus.core.validation.booleanTypes
import dev.formhaus.core.validation.fillTemplate
import dev.formhaus.core.validation.formatText
import dev.formhaus.core.validation.hasRecallBraces
import dev.formhaus.core.visibility.itemsOf

public data class RecallItem(public val group: String, public val index: Int)

public class RecallOptions(
    public val item: RecallItem? = null,
    public val lang: String? = null,
    public val format: RecallFormatFn? = null,
)

internal typealias KeyResolver = (String) -> String

internal fun substitute(definition: FormDefinition, text: String, params: JsonObject? = null, resolver: () -> KeyResolver): String {
    if (!definition.recallsAnswers) return params?.let { formatText(text, it) } ?: text
    if (if (params == null) !hasRecallBraces(text) else '{' !in text) return text
    val resolve by lazy(LazyThreadSafetyMode.NONE, resolver)
    return fillTemplate(text) { raw, token ->
        when (token) {
            is TemplateToken.Param -> params?.get(token.name)?.let(::jsString) ?: raw
            TemplateToken.Escape -> "{{"
            is TemplateToken.Recall -> token.key?.let(resolve) ?: raw
        }
    }
}

private fun answerText(field: FormField, item: JsonValue): RecallAnswer {
    item.objectValue?.let { return RecallAnswer.Text(it["name"]?.stringValue ?: "") }
    val label = field.optionIndex?.position(item)?.let { field.options?.get(it)?.label }
    if (label != null) return RecallAnswer.Text(label)
    if (item is JsonValue.Number) return RecallAnswer.Number(item.value)
    return RecallAnswer.Text(jsString(item))
}

internal fun recallAnswer(field: FormField, value: JsonValue?): RecallAnswer? {
    if (value == null || value.isNull) return null
    if (field.isGroup) return value.arrayValue?.let { RecallAnswer.Number(it.size.toDouble()) }
    if (field.type in booleanTypes) return if (value == JsonValue.Bool(true)) field.label?.let(RecallAnswer::Text) else null
    val items = value.arrayValue
    if (items == null) {
        val single = answerText(field, value)
        return if (single is RecallAnswer.Text && single.text.isBlankText) null else single
    }
    if (items.isEmpty()) return null
    return RecallAnswer.Items(items.map { item -> answerText(field, item).let { if (it is RecallAnswer.Text) it.text else it.stored } })
}

internal class RecallScope(
    private val visibility: VisibilityState,
    private val values: JsonObject,
    private val lang: String? = null,
    private val format: RecallFormatFn? = null,
    private val active: (String) -> FormField?,
) {
    fun resolver(options: RecallOptions): KeyResolver {
        val index = visibility.index
        val group = options.item?.let { item -> active(item.group)?.takeIf { it.isGroup } }
        val entry = group?.let { itemsOf(values[it.key]).getOrNull(options.item.index)?.objectValue }
        val scope = if (entry == null) emptyList() else group.items
        val shared = if (scope.isEmpty()) emptyList() else index.fieldsByKey[group?.key ?: ""].orEmpty().filter { it.isGroup }.flatMap { it.items }
        val locale = listOfNotNull(options.lang, lang, visibility.definition.lang).firstOrNull { it.isNotEmpty() } ?: "en"
        val format = options.format ?: format ?: defaultRecallFormat
        return { key ->
            val own = scope.firstOrNull { it.key == key }
            val candidates = if (own == null) index.fieldsByKey[key].orEmpty() else shared.filter { it.key == key }
            val field = if (candidates.any { it.type == "password" }) null else own ?: active(key)
            val source = if (own == null) values else entry ?: MutableJsonObject()
            val answer = if (field != null && key in source) recallAnswer(field, source[key]) else null
            if (field != null && answer != null) format(answer, field, locale) else ""
        }
    }
}

public fun recallText(definition: FormDefinition, text: String, values: JsonObject, options: RecallOptions = RecallOptions()): String =
    substitute(definition, text) {
        val visibility = VisibilityState(definition)
        RecallScope(visibility, values) { visibility.activeField(it, values) }.resolver(options)
    }

public fun recallStatic(definition: FormDefinition, text: String, group: String? = null): String = substitute(definition, text) {
    val fields = definition.allFields
    val scope = fields.firstOrNull { it.isGroup && it.key == group }?.items.orEmpty()
    val resolve: KeyResolver = { key ->
        val field = scope.firstOrNull { it.key == key } ?: fields.firstOrNull { it.key == key }
        field?.let { it.label?.takeIf(String::isNotEmpty) ?: key } ?: ""
    }
    resolve
}
