package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.conditionFields
import dev.formhaus.core.visibility.ListLimits

internal data class ItemContext(val values: List<JsonValue?>, val lengths: List<Int?>, val epoch: Int) {
    companion object {
        val FIXED: ItemContext = ItemContext(emptyList(), emptyList(), 0)
    }
}

internal class Mapped<Output>(val value: Output, val keep: Boolean)

internal class MappedItems<Output>(val items: List<Output>, val reused: Boolean)

internal class ItemCache<Output> {
    private class Sources(val outer: List<String>, val all: List<String>)

    private class Entry<Output>(
        val context: ItemContext,
        val ids: List<Int>,
        val inputs: List<JsonValue>,
        val outputs: List<Output?>,
        val whole: List<Output>?,
    ) {
        fun output(position: Int, item: JsonValue, ids: List<Int>): Output? {
            if (position >= inputs.size) return null
            val output = outputs[position] ?: return null
            if (ids.getOrNull(position) != this.ids.getOrNull(position) || inputs[position] != item) return null
            return output
        }
    }

    private val sources = HashMap<Int, Sources>()
    private val entries = HashMap<Int, Entry<Output>>()

    private fun sources(group: FormField): Sources = sources.getOrPut(group.ordinal) {
        val items = group.items
        val inner = items.mapTo(HashSet()) { it.key }
        val all = orderedKeys(items.flatMap { it.conditionFields })
        Sources(all.filter { it !in inner }, all)
    }

    fun context(group: FormField, scope: JsonObject, limits: ListLimits, epoch: Int = 0): ItemContext {
        val keys = sources(group)
        return ItemContext(keys.outer.map { scope[it] }, keys.all.map(limits::length), epoch)
    }

    fun map(
        group: FormField,
        items: List<JsonValue>,
        ids: List<Int> = emptyList(),
        context: ItemContext,
        sharing: Boolean = false,
        transform: (Int, JsonValue) -> Mapped<Output>,
    ): MappedItems<Output> {
        val previous = entries[group.ordinal]?.takeIf { it.context == context }
        val known = previous?.whole
        if (known != null && previous.inputs == items && previous.ids == ids) return MappedItems(known, true)
        val outputs = ArrayList<Output?>(items.size)
        val built = ArrayList<Output>(items.size)
        val changed = ArrayList<Int>()
        items.forEachIndexed { position, item ->
            val output = previous?.output(position, item, ids)
            if (output != null) {
                outputs.add(output)
                built.add(output)
                return@forEachIndexed
            }
            val mapped = transform(position, item)
            outputs.add(if (mapped.keep) mapped.value else null)
            built.add(mapped.value)
            changed.add(position)
        }
        val result = if (sharing && known != null && known.size == built.size && changed.all { known[it] == built[it] }) known else built
        val complete = outputs.all { it != null }
        entries[group.ordinal] = Entry(context, ids, items, outputs, if (complete) result else null)
        return MappedItems(result, false)
    }
}
