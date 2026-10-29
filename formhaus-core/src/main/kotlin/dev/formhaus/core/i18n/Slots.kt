package dev.formhaus.core.i18n

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.validation.MessageKey
import dev.formhaus.core.validation.defaultMessageKeys
import dev.formhaus.core.validation.defaultMessages
import dev.formhaus.core.validation.formatText
import dev.formhaus.core.validation.listTypes
import dev.formhaus.core.validation.noParams
import dev.formhaus.core.validation.typeMessageKeys

internal enum class SlotKind { TEXT, RULE, MESSAGE, LABEL }

internal class Slot(
    val kind: SlotKind,
    val address: List<String>,
    val key: String?,
    val source: String?,
    val fallback: String,
    val params: JsonObject,
    val template: Boolean,
    val location: List<String>,
    val property: String,
)

private val bounds = mapOf("minLength" to "min", "maxLength" to "max", "min" to "min", "max" to "max")

private fun ruleKey(
    field: FormField,
    rule: String,
): String =
    if ((field.type in listTypes || field.isGroup || field.isFileList) && rule.endsWith("Length")) {
        rule.replace("Length", "Items")
    } else {
        rule
    }

private fun catalogKeys(value: JsonValue?): List<String> = value?.objectValue?.keys.orEmpty()

private fun literalName(rule: String): String = if (rule == "required") rule else "${rule}Message"

private fun usedMessages(definition: FormDefinition): Set<String> {
    val keys = HashSet(catalogKeys(definition.messages) + catalogKeys(definition.i18n?.get("messages")))
    for (field in definition.allFields.flatMap { listOf(it) + it.items }) {
        val rules = field.validation?.json ?: noParams
        for (rule in NodeKind.VALIDATION.slots) {
            if (hasRule(rules, rule) && rules[literalName(rule)]?.stringValue == null) keys.add(ruleKey(field, rule))
        }
        typeMessageKeys(field).mapTo(keys) { it.rawValue }
    }
    return keys
}

private fun textSlot(
    text: TextNode,
    slot: String,
    keys: JsonObject,
): Slot? {
    val source = text.node[slot]?.stringValue ?: return null
    return Slot(
        SlotKind.TEXT,
        slotAddress(text.kind, text.address, slot),
        keys[slot]?.stringValue,
        source,
        source,
        noParams,
        true,
        text.location,
        slot,
    )
}

private fun ruleSlot(
    definition: FormDefinition,
    text: TextNode,
    slot: String,
    keys: JsonObject,
): Slot? {
    val field = text.field
    if (!hasRule(text.node, slot) || field == null) return null
    val literalName = literalName(slot)
    val literal = text.node[literalName]?.stringValue
    val params = MutableJsonObject()
    bounds[slot]?.let { bound -> text.node[slot]?.let { params[bound] = it } }
    val message = ruleKey(field, slot)
    val template = definition.messages?.get(message)?.stringValue ?: defaultMessages[MessageKey(message)].orEmpty()
    return Slot(
        SlotKind.RULE,
        text.address + slot,
        keys[slot]?.stringValue,
        literal,
        literal ?: formatText(template, params),
        params,
        literal != null || keys[slot]?.isTruthy == true,
        text.location,
        literalName,
    )
}

private fun nodeSlots(
    definition: FormDefinition,
    text: TextNode,
): List<Slot> {
    val keys = text.node["i18n"]?.objectValue ?: noParams
    return text.kind.slots.mapNotNull { slot ->
        if (text.kind == NodeKind.VALIDATION) ruleSlot(definition, text, slot, keys) else textSlot(text, slot, keys)
    }
}

private fun catalogSlots(
    definition: FormDefinition,
    name: String,
    used: Set<String>,
): List<Slot> {
    val isMessages = name == "messages"
    val order = if (isMessages) defaultMessageKeys.map { it.rawValue } else defaultLabelKeys
    val keys = definition.i18n?.get(name)?.objectValue ?: noParams
    return order.mapNotNull { item ->
        val fallback = if (isMessages) defaultMessages[MessageKey(item)] else defaultLabels[item]
        val source = definition.json[name]?.get(item)?.stringValue ?: fallback ?: return@mapNotNull null
        Slot(
            if (isMessages) SlotKind.MESSAGE else SlotKind.LABEL,
            listOf(name, item),
            keys[item]?.stringValue,
            source,
            source,
            noParams,
            item in used,
            listOf(name),
            item,
        )
    }
}

private val fileListLabels = listOf("removeItem", "removeItemNamed", "itemAdded", "itemRemoved", "fileCount")

private val groupLabels =
    listOf(
        "addItem",
        "removeItem",
        "itemLabel",
        "itemAdded",
        "itemRemoved",
        "removeItemNamed",
        "removeItemPrompt",
        "removeItemBody",
        "cancel",
    )
private val pickerTypes = listOf("date", "time", "datetime")

private fun unlabeled(
    value: JsonValue?,
    key: String,
): Boolean = value?.get(key)?.isTruthy != true

private fun usedLabels(definition: FormDefinition): Set<String> {
    val steps = definition.steps.orEmpty()
    val nodes = definition.allFields
    val fields = nodes.flatMap { if (it.isGroup) it.items else listOf(it) }
    val labels = hashSetOf("errorSummary")

    fun add(
        present: Boolean,
        keys: List<String>,
    ) {
        if (present) labels.addAll(keys)
    }
    add(steps.isNotEmpty(), listOf("continue", "back", "stepOf", "stepErrors"))
    add(steps.any { it.json["title"]?.isTruthy != true }, listOf("stepTitle"))
    add(steps.any { it.canSkip && unlabeled(it.json["skip"], "label") }, listOf("skip"))
    add(unlabeled(definition.json["submit"], "label"), listOf("submit"))
    add(nodes.any { it.isGroup }, groupLabels)
    add(fields.any { it.hasPictures }, listOf("enlargeImage", "close"))
    add(
        fields.any { field ->
            field.type == "multiselect" && field.options.orEmpty().any { it.exclusive }
        },
        listOf("or"),
    )
    add(fields.any { it.type == "select" }, listOf("select"))
    add(fields.any { it.validation?.required?.isTruthy == true }, listOf("required"))
    add(fields.any { it.type in pickerTypes || it.type == "file" }, listOf("clear"))
    add(fields.any { it.type in pickerTypes }, listOf("done"))
    add(fields.any { it.type == "file" }, listOf("chooseFile"))
    add(fields.any { it.isFileList }, fileListLabels)
    return labels
}

internal fun definitionSlots(definition: FormDefinition): List<Slot> =
    textNodes(definition).flatMap { nodeSlots(definition, it) } +
        catalogSlots(definition, "messages", usedMessages(definition)) +
        catalogSlots(definition, "labels", usedLabels(definition))
