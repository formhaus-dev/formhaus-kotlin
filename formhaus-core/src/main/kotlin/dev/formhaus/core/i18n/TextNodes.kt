package dev.formhaus.core.i18n

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.FormSection

internal enum class NodeKind(val slots: List<String>) {
    FORM(listOf("title", "description")),
    STEP(listOf("title", "description")),
    SECTION(listOf("title", "description")),
    FIELD(listOf("label", "placeholder", "helperText")),
    GROUP(listOf("label", "helperText", "itemLabel", "addLabel", "removeLabel")),
    OPTION(listOf("label")),
    ACTION(listOf("label")),
    VALIDATION(listOf("required", "minLength", "maxLength", "min", "max", "pattern", "matchField")),
}

internal fun hasRule(node: JsonObject, slot: String): Boolean {
    val value = node[slot]
    if (slot == "required") return value?.isTruthy == true
    return value != null && value != JsonValue.Bool(false) && value != JsonValue.String("")
}

internal class TextNode(
    val kind: NodeKind,
    val node: JsonObject,
    val location: List<String>,
    val address: List<String>,
    val field: FormField?,
) {
    val path: String get() = location.joinToString("") { "/$it" }
}

private class TextNodeCollector {
    val list = ArrayList<TextNode>()

    fun add(kind: NodeKind, node: JsonValue?, location: List<String>, address: List<String>, field: FormField? = null) {
        val json = node?.objectValue ?: return
        list.add(TextNode(kind, json, location, address, field))
    }

    fun actions(owner: JsonObject, location: List<String>, address: List<String>, names: List<String>) {
        for (name in names) add(NodeKind.ACTION, owner[name], location + name, address + name)
    }

    fun fields(items: List<FormNode>, location: List<String>, address: List<String>) {
        items.forEachIndexed { index, node ->
            val at = location + listOf("fields", index.toString())
            when (node) {
                is FormSection -> {
                    add(NodeKind.SECTION, JsonValue.Object(node.json), at, address + listOf("sections", node.key))
                    fields(node.fields, at, address)
                }
                is FormField -> field(node, at, address + listOf("fields", node.key))
            }
        }
    }

    private fun field(field: FormField, at: List<String>, key: List<String>) {
        add(if (field.isGroup) NodeKind.GROUP else NodeKind.FIELD, JsonValue.Object(field.json), at, key)
        field.options.orEmpty().forEachIndexed { position, option ->
            add(NodeKind.OPTION, JsonValue.Object(option.json), at + listOf("options", position.toString()), key + listOf("options", option.value.orEmpty()))
        }
        add(NodeKind.VALIDATION, field.json["validation"], at + "validation", key + "validation", field)
        if (field.isGroup) fields(field.fields.orEmpty(), at, key)
    }
}

internal fun textNodes(definition: FormDefinition): List<TextNode> {
    val collector = TextNodeCollector()
    collector.add(NodeKind.FORM, JsonValue.Object(definition.json), emptyList(), emptyList())
    collector.actions(definition.json, emptyList(), emptyList(), listOf("submit", "cancel"))
    val steps = definition.steps.orEmpty()
    steps.forEachIndexed { index, step ->
        val location = listOf("steps", index.toString())
        val address = listOf("steps", step.id)
        collector.add(NodeKind.STEP, JsonValue.Object(step.json), location, address)
        collector.actions(step.json, location, address, listOf("next", "back", "skip"))
        collector.fields(step.fields, location, address)
    }
    if (steps.isEmpty()) collector.fields(definition.fields.orEmpty(), emptyList(), emptyList())
    return collector.list
}

internal fun slotAddress(kind: NodeKind, address: List<String>, slot: String): List<String> =
    if (kind == NodeKind.OPTION) address else address + slot
