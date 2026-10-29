package dev.formhaus.core.validation

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.isInteger
import dev.formhaus.core.json.jsNumber
import dev.formhaus.core.json.jsTrimmed
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.ScaleRange
import dev.formhaus.core.regex.JsRegex
import dev.formhaus.core.visibility.isBlank
import kotlin.math.floor

private const val DEFAULT_GROUP_ITEMS = 100.0

private val decimal = JsRegex.unchecked("^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?$")
private val untrimmedTypes = setOf("password", "select", "radio", "file") + numberTypes

internal val FormField.fileListMax: Double get() =
    floor(validation?.maxLength?.takeIf { it.isFinite() } ?: DEFAULT_GROUP_ITEMS).coerceIn(0.0, MAX_GROUP_ITEMS.toDouble())

internal val FormField.maxItems: Double get() =
    minOf(
        validation?.maxLength ?: DEFAULT_GROUP_ITEMS,
        MAX_GROUP_ITEMS.toDouble(),
    )

private val FormField.isListField: Boolean get() = type in listTypes || isFileList

internal fun trimValue(
    field: FormField,
    value: JsonValue?,
): JsonValue? {
    if (value !is JsonValue.String || field.type in untrimmedTypes) return value
    return JsonValue.String(value.value.jsTrimmed)
}

internal fun normalizeValue(
    field: FormField,
    input: JsonValue?,
): JsonValue? {
    if (input?.arrayValue?.isEmpty() == true && !field.isListField && field.type in builtInTypes) return null
    if (field.isFileList && (input is JsonValue.Array || (input is JsonValue.String && input.value.isNotEmpty()))) {
        return JsonValue.Array(fileItems(input))
    }
    if (field.isGroup) return normalizeItems(field, input)
    val value = trimValue(field, input)
    if (field.type !in numberTypes || value !is JsonValue.String || decimal.test(value.value) != true) return value
    val parsed = jsNumber(value.value)
    return if (parsed.isFinite()) JsonValue.Number(parsed) else value
}

private fun normalizeItems(
    group: FormField,
    input: JsonValue?,
): JsonValue? {
    val items = input?.arrayValue ?: return input
    if (items.size > group.maxItems) return input
    return JsonValue.Array(
        items.map { item ->
            val entry = item.objectValue ?: return@map item
            val result = MutableJsonObject()
            for (field in group.items) {
                if (field.key in entry) result.assign(field.key, normalizeValue(field, entry[field.key]))
            }
            JsonValue.Object(result)
        },
    )
}

private fun staticOptions(field: FormField): List<FieldOption>? = if (field.hasOptionsFrom) null else field.options

internal fun exclusiveValues(field: FormField): List<JsonValue> {
    if (field.type !in listTypes) return emptyList()
    return staticOptions(field).orEmpty().filter { it.exclusive }.mapNotNull { it.json["value"] }
}

private fun isOption(
    field: FormField,
    value: JsonValue,
): Boolean {
    if (field.hasOptionsFrom) return true
    val index = field.optionIndex ?: return true
    return index.position(value) != null
}

private typealias Fail = (MessageKey, JsonObject) -> String

private fun lengthError(
    field: FormField,
    value: String,
    fail: Fail,
): String? {
    val fallback = (if (field.type == "hidden") MAX_HIDDEN_LENGTH else MAX_STRING_LENGTH).toDouble()
    val max = minOf(MAX_STRING_LENGTH.toDouble(), field.validation?.maxLength ?: fallback)
    if (value.length <= max) return null
    return field.validation?.message("maxLengthMessage")
        ?: fail(MessageKey.MAX_LENGTH, stringMapOf("max" to JsonValue.Number(max)))
}

private fun multiselectError(
    field: FormField,
    value: JsonValue,
    fail: Fail,
): String? {
    val items = value.arrayValue ?: return fail(MessageKey.OPTIONS, noParams)
    if (items.size > (staticOptions(field)?.size ?: MAX_LIST_ITEMS)) return fail(MessageKey.OPTION, noParams)
    val seen = HashSet<String>()
    var repeated = false
    for (item in items) {
        val text = item.stringValue
        if (text == null || text.length > MAX_STRING_LENGTH ||
            !isOption(field, item)
        ) {
            return fail(MessageKey.OPTION, noParams)
        }
        repeated = !seen.add(text) || repeated
    }
    if (repeated) return fail(MessageKey.OPTIONS, noParams)
    if (items.size <= 1) return null
    val exclusive =
        staticOptions(field)?.firstOrNull { option ->
            option.exclusive && option.json["value"]?.stringValue?.let(seen::contains) == true
        } ?: return null
    return fail(
        MessageKey.EXCLUSIVE,
        stringMapOf("option" to (exclusive.json["label"] ?: JsonValue.String("undefined"))),
    )
}

private fun stringError(
    field: FormField,
    value: JsonValue,
    key: MessageKey,
    fail: Fail,
): String? {
    val text = value.stringValue ?: return fail(key, noParams)
    return lengthError(field, text, fail)
}

private fun formatError(
    field: FormField,
    value: JsonValue,
    format: ValueFormat,
    fail: Fail,
): String? {
    val text = value.stringValue ?: return fail(format.key, noParams)
    lengthError(field, text, fail)?.let { return it }
    return if (text.length <= format.max && format.matches(text)) null else fail(format.key, noParams)
}

private fun primitiveError(
    field: FormField,
    value: JsonValue,
    fail: Fail,
): String? {
    if (value.boolValue != null || value.isFiniteNumber) return null
    return stringError(field, value, MessageKey.VALUE, fail)
}

private fun isEmpty(
    field: FormField,
    value: JsonValue?,
): Boolean = isBlank(value) && (field.isListField || value?.arrayValue == null)

private fun groupError(
    field: FormField,
    value: JsonValue,
    fail: Fail,
): String? {
    val max = field.validation?.maxLength
    val items = value.arrayValue ?: return fail(MessageKey.VALUE, noParams)
    if (max != null && items.size > max) {
        return field.validation.message("maxLengthMessage")
            ?: fail(MessageKey.MAX_ITEMS, stringMapOf("max" to JsonValue.Number(max)))
    }
    return if (items.size > field.maxItems ||
        !items.all { it.objectValue != null }
    ) {
        fail(MessageKey.VALUE, noParams)
    } else {
        null
    }
}

private fun isReference(item: JsonValue): Boolean = item.stringValue?.let { it.isNotEmpty() && it.length <= MAX_STRING_LENGTH } == true

internal fun FormField.maxFilesMessage(resolve: (MessageKey, JsonObject) -> String): String =
    validation?.message("maxLengthMessage") ?: resolve(MessageKey.MAX_ITEMS, stringMapOf("max" to JsonValue.Number(fileListMax)))

private fun isFileValue(item: JsonValue): Boolean = item.stringValue != null || item.objectValue?.get("name")?.stringValue != null

private fun fileListError(
    field: FormField,
    value: JsonValue,
    fail: Fail,
): String? {
    val items = value.arrayValue ?: return fail(MessageKey.FILE, noParams)
    if (items.size > field.fileListMax) return field.maxFilesMessage(fail)
    return if (items.all(::isReference)) null else fail(MessageKey.FILE, noParams)
}

internal fun typeError(
    field: FormField,
    input: JsonValue?,
    resolve: ResolveMessage = messageResolver(),
): String? {
    val value = input ?: return null
    if (isEmpty(field, value)) return null
    val fail: Fail = { key, params -> resolve(key, params, field) }
    field.scaleRange?.let { return scaleError(it, value, fail) }
    return when {
        field.isGroup -> groupError(field, value, fail)
        field.type in booleanTypes -> if (value.boolValue != null) null else fail(MessageKey.BOOLEAN, noParams)
        field.type == "number" -> if (value.isFiniteNumber) null else fail(MessageKey.NUMBER, noParams)
        else -> choiceOrTextError(field, value, fail)
    }
}

private fun scaleError(
    range: ScaleRange,
    value: JsonValue,
    fail: Fail,
): String? {
    val point = value.numberValue
    val valid = point != null && point.isInteger && range.min <= point && point <= range.max
    return if (valid) null else fail(MessageKey.OPTION, noParams)
}

private fun choiceOrTextError(
    field: FormField,
    value: JsonValue,
    fail: Fail,
): String? {
    val format = ValueFormat.of(field.type)
    return when {
        field.type in listTypes -> {
            multiselectError(field, value, fail)
        }

        field.isFileList -> {
            fileListError(field, value, fail)
        }

        field.type == "file" -> {
            stringError(field, value, MessageKey.FILE, fail)
        }

        format != null -> {
            formatError(field, value, format, fail)
        }

        field.type == "select" || field.type == "radio" -> {
            stringError(field, value, MessageKey.OPTION, fail) ?: if (isOption(field, value)) null else fail(MessageKey.OPTION, noParams)
        }

        field.type in textTypes -> {
            stringError(field, value, MessageKey.STRING, fail)
        }

        else -> {
            primitiveError(field, value, fail)
        }
    }
}

internal fun typeMessageKeys(field: FormField): List<MessageKey> {
    val format = ValueFormat.of(field.type)
    return when {
        field.isGroup -> {
            listOf(MessageKey.VALUE)
        }

        field.type in booleanTypes -> {
            listOf(MessageKey.BOOLEAN)
        }

        field.type == "number" -> {
            listOf(MessageKey.NUMBER)
        }

        field.isScale -> {
            listOf(MessageKey.OPTION)
        }

        field.type in listTypes -> {
            listOf(MessageKey.OPTIONS, MessageKey.OPTION) +
                if (exclusiveValues(field).isEmpty()) emptyList() else listOf(MessageKey.EXCLUSIVE)
        }

        format != null -> {
            listOf(format.key, MessageKey.MAX_LENGTH)
        }

        field.type == "file" -> {
            listOf(MessageKey.FILE, if (field.isFileList) MessageKey.MAX_ITEMS else MessageKey.MAX_LENGTH)
        }

        field.type == "select" || field.type == "radio" -> {
            listOf(MessageKey.OPTION, MessageKey.MAX_LENGTH)
        }

        else -> {
            listOf(if (field.type in textTypes) MessageKey.STRING else MessageKey.VALUE, MessageKey.MAX_LENGTH) +
                if (field.type == "hidden") listOf(MessageKey.HIDDEN) else emptyList()
        }
    }
}

internal fun clientTypeError(
    field: FormField,
    value: JsonValue?,
    resolve: ResolveMessage,
): String? {
    if (field.isFileList) return if (fileItems(value).all(::isFileValue)) null else resolve(MessageKey.FILE, noParams, field)
    if (field.type == "file" || (field.type !in builtInTypes && !field.isGroup)) return null
    return typeError(field, value, resolve)
}
