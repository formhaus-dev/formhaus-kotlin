package dev.formhaus.core.engine

import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.isConditioned
import dev.formhaus.core.visibility.isVisible
import dev.formhaus.core.visibility.itemPath
import dev.formhaus.core.visibility.itemScope
import dev.formhaus.core.visibility.itemsOf

internal class TrackedGroup(
    val group: FormField,
    val fields: List<FormField>,
)

internal class GroupShown(
    val key: String,
    val fields: List<String>,
    val ids: List<String>,
    val rows: List<List<Boolean>>,
)

internal class Flip(
    val key: String,
    val shown: Boolean,
)

internal class ShownFields(
    val token: Int,
    val top: Map<String, Flip>,
    val groups: List<GroupShown>,
) {
    val flat: Map<String, Flip>
        get() {
            val result = LinkedHashMap(top)
            for (group in groups) {
                group.ids.forEachIndexed { position, id ->
                    group.fields.forEachIndexed { index, field ->
                        result["${group.key} $id $field"] =
                            Flip(itemPath(group.key, position, field), group.rows[position][index])
                    }
                }
            }
            return result
        }
}

internal class ShownCache(
    index: DefinitionIndex,
    groups: List<FormField>,
) {
    val tracked: List<TrackedGroup> =
        groups.mapNotNull { group ->
            val fields = group.items.filter { it.isConditioned }
            if (fields.isEmpty()) null else TrackedGroup(group, fields)
        }
    val incremental: Boolean =
        tracked.all { tracked ->
            !index.isShared(tracked.group.key) && orderedKeys(tracked.fields.map { it.key }).size == tracked.fields.size
        }
    val rows = ItemCache<List<Boolean>>()
    var token = 0
}

internal class TrackedShown(
    val state: ShownFields,
    val flips: List<Flip>,
)

internal fun VisibilityState.trackShown(
    values: Values,
    previous: ShownFields?,
): TrackedShown {
    val cache = shown
    val fast = cache.incremental && previous?.token == cache.token
    val top = LinkedHashMap<String, Flip>()
    for (field in conditional) {
        top[field.key] =
            Flip(field.key, top[field.key]?.shown == true || visible(field, values.json))
    }
    val flips = ArrayList<Flip>()
    if (fast) {
        for ((id, entry) in top) if (previous.top[id]?.shown == !entry.shown) flips.add(entry)
    }
    val groups =
        cache.tracked.mapIndexed {
            offset,
            tracked,
            ->
            shownRows(tracked, values, if (fast) previous.groups[offset] else null, flips)
        }
    cache.token = freshToken()
    val state = ShownFields(cache.token, top, groups)
    if (fast || previous == null) return TrackedShown(state, flips)
    val before = previous.flat
    return TrackedShown(
        state,
        state.flat.mapNotNull { (id, entry) ->
            if (before[id]?.shown == !entry.shown) {
                entry
            } else {
                null
            }
        },
    )
}

private fun VisibilityState.shownRows(
    tracked: TrackedGroup,
    values: Values,
    previous: GroupShown?,
    flips: MutableList<Flip>,
): GroupShown {
    val scope = scope(values.json)
    val limits = limits(values.json)
    val key = tracked.group.key
    val items = itemsOf(values[key])
    val fields = tracked.group.items
    val names = tracked.fields.map { it.key }
    val known by lazy {
        previous
            ?.ids
            ?.withIndex()
            ?.reversed()
            ?.associate { (index, id) -> id to index }
            .orEmpty()
    }
    val context = shown.rows.context(tracked.group, scope, limits)
    val mapped =
        shown.rows.map(tracked.group, items, values.itemIds(key), context) { position, item ->
            val inner = itemScope(fields, item, scope)
            val row = tracked.fields.map { isVisible(it, inner, limits) }
            val before = previous?.let { known[itemId(values, key, position, item)] }
            if (previous != null && before != null) {
                names.forEachIndexed { index, name ->
                    if (previous.rows[before][index] == !row[index]) {
                        flips.add(Flip(itemPath(key, position, name), row[index]))
                    }
                }
            }
            Mapped(row, true)
        }
    if (mapped.reused && previous != null) return previous
    return GroupShown(
        key,
        names,
        items.mapIndexed {
            position,
            item,
            ->
            itemId(values, key, position, item)
        },
        mapped.items,
    )
}
