package dev.formhaus.core.model

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue

public sealed class FormNode : Conditional {
    internal abstract val json: JsonObject
    internal abstract val ordinal: Int
    public abstract val key: String
    public abstract val type: String
    internal abstract val children: List<FormNode>?
}

public class FormSection private constructor(
    override val json: JsonObject,
    override val ordinal: Int,
    public val fields: List<FormNode>,
) : FormNode() {
    internal constructor(
        json: JsonObject,
        ordinals: Ordinals,
    ) : this(json, ordinals.take(), formNodes(json["fields"], ordinals).orEmpty())

    override val key: String = json["key"]?.stringValue ?: ""
    override val type: String get() = "section"
    override val show: List<ShowCondition>? = conditions(json["show"])
    override val showAny: List<ShowCondition>? = conditions(json["showAny"])
    override val children: List<FormNode> get() = fields

    public val title: String? get() = json["title"]?.stringValue
    public val description: String? get() = json["description"]?.stringValue
    public val layout: String? get() = json["layout"]?.stringValue
    public val isMatrix: Boolean get() = layout == "matrix"

    internal fun filtered(include: (FormField) -> Boolean): FormSection =
        FormSection(json, ordinal, fields.filter { it is FormField && include(it) })
}

internal fun formNodes(
    value: JsonValue?,
    ordinals: Ordinals,
): List<FormNode>? =
    value?.arrayValue?.mapNotNull { item ->
        val json = item.objectValue ?: return@mapNotNull null
        if (json["type"]?.stringValue == "section") FormSection(json, ordinals) else FormField(json, ordinals)
    }

internal fun valueFields(nodes: List<FormNode>): List<FormField> =
    nodes.flatMap { node ->
        when (node) {
            is FormField -> listOf(node)
            is FormSection -> node.fields.filterIsInstance<FormField>()
        }
    }
