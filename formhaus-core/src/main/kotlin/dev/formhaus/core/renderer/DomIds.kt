package dev.formhaus.core.renderer

import dev.formhaus.core.i18n.fnv1a
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.jsString
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.FormSection

private const val HASH_RADIX = 36
private const val MAX_SCALE_POINTS = 11
private const val NUMBERED_MARK = "#"
private const val DESCRIPTION = "-description"
private const val REVEAL = "reveal"

private fun isIdChar(char: Char): Boolean = char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char == '_' || char == '-'

private fun clean(value: String): String = buildString(value.length) { for (char in value) append(if (isIdChar(char)) char else '-') }

private fun base(key: String): String {
    val cleaned = clean(key)
    if (!cleaned.contains("--$REVEAL")) return cleaned
    return buildString(cleaned.length) {
        var index = 0
        while (index < cleaned.length) {
            var end = index
            while (end < cleaned.length && cleaned[end] == '-') end += 1
            if (end - index >= 2 && cleaned.startsWith(REVEAL, end)) {
                append('-')
            } else {
                append(cleaned, index, maxOf(end, index + 1))
            }
            index = maxOf(end, index + 1)
        }
    }
}

private fun numberedTail(
    text: String,
    from: Int,
): Boolean {
    var index = from
    while (index < text.length && text[index] in '0'..'9') index += 1
    if (index == from) return false
    return index == text.length || (text.length - index == DESCRIPTION.length && text.startsWith(DESCRIPTION, index))
}

private fun isNumbered(part: String): Boolean = part.startsWith('-') && numberedTail(part, 1)

private fun numberedEnd(id: String): Int {
    var index = id.indexOf('-')
    while (index >= 0) {
        if (numberedTail(id, index + 1)) return index
        index = id.indexOf('-', index + 1)
    }
    return 0
}

private fun Set<String>.holds(part: String): Boolean = contains(part) || (contains(NUMBERED_MARK) && isNumbered(part))

private fun Set<String>.covers(
    rest: String,
    parts: List<String>,
): Boolean = holds(rest) || parts.any { holds(rest + it) }

private fun ends(
    id: String,
    longest: Int,
): List<Int> {
    val found = ArrayList<Int>()
    var index = id.lastIndexOf('-')
    while (index > 0 && index >= id.length - longest) {
        found.add(index)
        index = id.lastIndexOf('-', index - 1)
    }
    return found
}

private val groupParts = listOf("-", "-confirm", "-confirm-title", "-confirm-description")
private val anchorParts = listOf("-anchor-0", "-anchor-1", "-anchor-2")
private val listedParts = listOf(NUMBERED_MARK, DESCRIPTION, "-0", "-1", "-2")

private fun JsonValue?.truthy(): Boolean = this?.isTruthy == true

private fun scaleParts(
    field: FormField,
    options: List<JsonValue>,
): List<String> {
    val range = field.scaleRange ?: return emptyList()
    val count = minOf(MAX_SCALE_POINTS.toDouble(), maxOf(0.0, range.max - range.min + 1))
    val points = (0 until count.toInt()).map { "-${jsString(JsonValue.Number(range.min + it))}" }
    return if (options.isEmpty()) points else points + anchorParts
}

private fun JsonValue.described(): Boolean = objectValue?.get("description").truthy()

private fun optionParts(
    field: FormField,
    options: List<JsonValue>,
): List<String> {
    val parts = options.flatMapIndexed { index, option -> listOf("-$index", if (option.described()) "-$index$DESCRIPTION" else "") }
    return when {
        field.json["optionsFrom"].truthy() || options.isEmpty() -> parts + listedParts
        options[0].described() -> parts + DESCRIPTION
        else -> parts
    }
}

private fun controlPart(field: FormField): String =
    when {
        field.type == "autocomplete" -> "-list"
        field.type == "file" && field.json["multiple"].truthy() -> "-notice"
        else -> ""
    }

private fun partsOf(node: FormNode): List<String> {
    if (node is FormSection) return if (node.json["description"].truthy()) listOf(DESCRIPTION) else emptyList()
    val field = node as? FormField ?: return emptyList()
    val type = field.type
    if (type == "hidden") return emptyList()
    val parts = mutableListOf("-error", if (field.json["helperText"].truthy()) "-helper" else "")
    if (field.isGroup) return parts + groupParts
    val choice = type == "radio" || type == "multiselect"
    val options = field.json["options"]?.arrayValue.orEmpty()
    if (!choice && !field.isScale && type != "checkbox") parts.add("-label")
    parts.add(controlPart(field))
    parts.addAll(scaleParts(field, options))
    if (choice) parts.addAll(optionParts(field, options))
    return parts
}

private fun ordered(
    names: Set<String>,
    groups: Set<String>,
): List<String> {
    val plain = names.filterTo(HashSet()) { base(it) == it }
    return names.sortedWith { a, b ->
        val grouped = (a in groups).compareTo(b in groups)
        when {
            grouped != 0 -> grouped
            a in groups -> b.compareTo(a)
            else -> (b in plain).compareTo(a in plain).takeIf { it != 0 } ?: a.compareTo(b)
        }
    }
}

private class Assignment(
    names: Map<String, List<String>>,
    private val groups: Set<String>,
) {
    private val taken = HashMap<String, Set<String>>()
    private val numbered = HashSet<String>()
    private val nested = HashSet<String>()
    private val tails = HashMap<String, MutableList<Pair<String, List<String>>>>()
    private val longest = names.values.maxOfOrNull { parts -> parts.maxOfOrNull { it.length } ?: 0 } ?: 0
    val ids: Map<String, String> =
        HashMap<String, String>().also { ids ->
            for (key in ordered(names.keys, groups)) ids[key] = assign(key, names.getValue(key))
        }

    private fun claims(
        id: String,
        index: Int,
        parts: List<String>,
    ): Boolean = taken[id.substring(0, index)]?.covers(id.substring(index), parts) == true

    private fun claimed(
        id: String,
        parts: List<String>,
    ): Boolean {
        val end = numberedEnd(id)
        return ends(id, longest).any { claims(id, it, parts) } || (end > 0 && claims(id, end, parts))
    }

    private fun claiming(
        id: String,
        own: Set<String>,
    ): Boolean =
        (NUMBERED_MARK in own && id in numbered) ||
            tails[id].orEmpty().any { (rest, other) -> own.covers(rest, other) }

    private fun clashes(
        key: String,
        id: String,
        parts: List<String>,
        own: Set<String>,
    ): Boolean = id in taken || (key in groups && id in nested) || claimed(id, parts) || claiming(id, own)

    private fun assign(
        key: String,
        parts: List<String>,
    ): String {
        val own = parts.toSet()
        var id = base(key)
        var seed = key
        while (clashes(key, id, parts, own)) {
            id = "${base(key)}-${Integer.toUnsignedLong(fnv1a(seed)).toString(HASH_RADIX)}"
            seed = id
        }
        taken[id] = own
        val end = numberedEnd(id)
        if (end > 0) numbered.add(id.substring(0, end))
        for (index in ends(id, longest)) tails.getOrPut(id.substring(0, index), ::ArrayList).add(id.substring(index) to parts)
        var index = id.indexOf("--")
        while (index >= 0) {
            nested.add(id.substring(0, index))
            index = id.indexOf("--", index + 1)
        }
        return id
    }
}

internal class DomIds(
    definition: FormDefinition,
) {
    private val scopes: Map<String, Map<String, String>>

    init {
        val names = linkedMapOf<String, LinkedHashMap<String, MutableList<String>>>("" to LinkedHashMap())
        val groups = HashSet<String>()

        fun add(
            scope: String,
            node: FormNode,
        ) {
            names.getOrPut(scope, ::LinkedHashMap).getOrPut(node.key, ::ArrayList).addAll(partsOf(node))
        }
        for (node in definition.topNodes.flatMap { if (it is FormSection) listOf(it) + it.fields else listOf(it) }) {
            add("", node)
            if (node !is FormField || !node.isGroup) continue
            groups.add(node.key)
            for (field in node.items) add(".${node.key}", field)
        }
        scopes = names.mapValues { (scope, keys) -> Assignment(keys, if (scope.isEmpty()) groups else emptySet()).ids }
    }

    private fun nodeId(
        key: String,
        scope: String,
    ): String = scopes[scope]?.get(key) ?: base(key)

    fun field(
        formId: String?,
        key: String,
    ): String = "${formId?.takeIf { it.isNotEmpty() }?.let(::clean) ?: "fh"}-${nodeId(key, "")}"

    fun item(
        formId: String?,
        group: String,
        index: Int,
        field: String,
    ): String = "${field(formId, group)}--$index--${nodeId(field, ".$group")}"
}
