package dev.formhaus.core.renderer

import dev.formhaus.core.i18n.fnv1a
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormSection

private const val HASH_RADIX = 36

private fun isIdChar(char: Char): Boolean = char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char == '_' || char == '-'

private fun clean(value: String): String = buildString(value.length) { for (char in value) append(if (isIdChar(char)) char else '-') }

private fun ordered(
    names: Set<String>,
    groups: Set<String>,
): List<String> =
    names.sortedWith { a, b ->
        val grouped = (a in groups).compareTo(b in groups)
        when {
            grouped != 0 -> grouped
            a in groups -> b.compareTo(a)
            else -> (clean(b) == b).compareTo(clean(a) == a).takeIf { it != 0 } ?: a.compareTo(b)
        }
    }

private fun assign(
    names: Set<String>,
    groups: Set<String>,
): Map<String, String> {
    val ids = HashMap<String, String>()
    val taken = LinkedHashSet<String>()
    for (key in ordered(names, groups)) {
        var id = clean(key)
        var seed = key
        while (id in taken || (key in groups && taken.any { it.startsWith("$id--") })) {
            id = "${clean(key)}-${Integer.toUnsignedLong(fnv1a(seed)).toString(HASH_RADIX)}"
            seed = id
        }
        taken.add(id)
        ids[key] = id
    }
    return ids
}

internal class DomIds(
    private val formId: String?,
    definition: FormDefinition,
) {
    private val scopes: Map<String, Map<String, String>>

    init {
        val names = linkedMapOf("" to LinkedHashSet<String>())
        val groups = HashSet<String>()
        for (node in definition.topNodes.flatMap { if (it is FormSection) listOf(it) + it.fields else listOf(it) }) {
            names.getValue("").add(node.key)
            if (node !is FormField || !node.isGroup) continue
            groups.add(node.key)
            for (field in node.items) names.getOrPut(".${node.key}", ::LinkedHashSet).add(field.key)
        }
        scopes = names.mapValues { (scope, keys) -> assign(keys, if (scope.isEmpty()) groups else emptySet()) }
    }

    private fun nodeId(
        key: String,
        scope: String,
    ): String = scopes[scope]?.get(key) ?: clean(key)

    fun field(key: String): String = "${formId?.takeIf { it.isNotEmpty() }?.let(::clean) ?: "fh"}-${nodeId(key, "")}"

    fun item(
        group: String,
        index: Int,
        field: String,
    ): String = "${field(group)}--$index--${nodeId(field, ".$group")}"
}
