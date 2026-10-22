package dev.formhaus.core.visibility

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.StringMap
import dev.formhaus.core.json.sameValueZero
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FormField
import dev.formhaus.core.validation.MAX_LIST_ITEMS
import dev.formhaus.core.validation.listTypes

private fun exemptLength(field: FormField): Int? {
    if (field.type !in listTypes || field.hasOptionsFrom) return null
    return field.options?.size?.takeIf { it > MAX_LIST_ITEMS }
}

internal class ListLimits private constructor(private val lengths: StringMap<Int>) {
    fun trimmed(fields: List<FormField>): ListLimits {
        val next = lengths.toMutableStringMap()
        for (field in fields.flatMap { listOf(it) + it.items }) next.assign(field.key, exemptLength(field))
        return ListLimits(next)
    }

    fun length(key: String): Int? = lengths[key]

    fun contains(key: String, value: JsonValue?, operand: JsonValue?): Boolean {
        val items = value?.arrayValue ?: return false
        if (items.size > MAX_LIST_ITEMS && items.size > (lengths[key] ?: 0)) return false
        return items.any { sameValueZero(it, operand) }
    }

    companion object {
        val STANDARD: ListLimits = ListLimits(stringMapOf())
    }
}
