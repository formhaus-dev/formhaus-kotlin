package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.json.sameValueZero
import dev.formhaus.core.model.FormField
import dev.formhaus.core.regex.CallBudget
import dev.formhaus.core.validation.exclusiveValues
import dev.formhaus.core.visibility.isVisible
import dev.formhaus.core.visibility.itemPath
import dev.formhaus.core.visibility.itemScope
import dev.formhaus.core.visibility.itemsOf
import dev.formhaus.core.visibility.splitPath

internal fun exclusiveWrite(
    field: FormField?,
    previous: JsonValue?,
    next: JsonValue?,
): JsonValue? {
    val items = next?.arrayValue
    if (field == null || items == null) return next
    val exclusive = exclusiveValues(field)
    if (exclusive.isEmpty()) return next
    val isExclusive = { value: JsonValue -> exclusive.any { sameValueZero(it, value) } }
    val before = previous?.arrayValue.orEmpty()
    val added = items.filter { item -> before.none { sameValueZero(it, item) } }
    added.lastOrNull(isExclusive)?.let { return JsonValue.Array(listOf(it)) }
    val rest = items.filterNot(isExclusive)
    return if (added.isNotEmpty() && rest.size < items.size) JsonValue.Array(rest) else next
}

internal fun EngineCore.writeValue(
    key: String,
    slot: ValueSlot,
    own: Boolean = true,
    after: () -> Unit = {},
) {
    CallBudget.run { storeValue(key, slot, own, after) }
}

private fun EngineCore.storeValue(
    key: String,
    slot: ValueSlot,
    own: Boolean,
    after: () -> Unit,
) {
    baselineFields()
    val previousSteps = if (isMultiStep) visibleSteps else null
    val valueChanged = !values.slot(key).isSame(slot)
    values.set(key, slot)
    visibility.starts.remember(key, slot, written = true)
    if (own) clearError(key, server = valueChanged)
    if (valueChanged) skipped.removeAll { key in it.keys }
    val cleared = reconcileHidden(written = key)
    dropServerErrors(cleared)
    clearResolvedMatchErrors(key)
    after()
    val valuesChanged = valueChanged || cleared.isNotEmpty()
    val structureChanged = valuesChanged && visibility.affectsStructure(key, cleared)
    if (previousSteps != null && structureChanged) {
        visibility.markChanged(structureChanged = true, valuesChanged = true)
        currentStepIndex = reconcileStepIndex(previousSteps, visibleSteps, currentStepIndex)
        pruneOffPathErrors()
    }
    notify(structureChanged, valuesChanged)
}

internal fun EngineCore.setValue(
    key: String,
    value: JsonValue?,
) {
    val path = visibility.parse(key)
    if (path != null) return setItemValue(path.group, path.index, path.field, value)
    writeValue(key, ValueSlot.fresh(exclusiveWrite(visibility.fieldOf(key, values.json), values[key], value)))
}

internal fun EngineCore.loadSubmitted(
    submitted: JsonObject,
    previous: JsonObject,
) {
    replaceValues(Values(submitted))
    reconcileHidden(initial = true)
    visibility.markChanged(structureChanged = true, valuesChanged = true)
    visibility.starts.loadPayload()
    visibility.starts.storeValues(previous)
    visibility.unsettle()
}

private fun EngineCore.activeGroup(key: String): FormField? =
    visibility.activeField(key, values.json)?.takeIf {
        it.isGroup
    }

internal fun itemId(
    values: Values,
    key: String,
    index: Int,
    item: JsonValue,
): String = if (item.objectValue != null) values.itemId(key, index)?.let { "i$it" } ?: "$index" else "$index"

internal fun EngineCore.items(key: String): List<GroupItem> {
    val group = activeGroup(key) ?: return emptyList()
    val fields = group.items
    val scope = visibility.scope(values.json)
    val limits = visibility.limits(values.json)
    val listed = visibility.listed
    val context = listed.context(group, scope, limits)
    return listed
        .map(group, itemsOf(values[key]), values.itemIds(key), context, sharing = true) { index, item ->
            val inner = itemScope(fields, item, scope)
            val shown = fields.filter { isVisible(it, inner, limits) }
            Mapped(GroupItem(itemId(values, key, index, item), if (shown.size == fields.size) fields else shown), true)
        }.items
}

internal fun EngineCore.addItem(key: String): Boolean {
    val items = itemsOf(values[key])
    val group = activeGroup(key) ?: return false
    if (items.size.toDouble() >= group.itemLimits.max) return false
    val id = freshToken()
    val item = JsonValue.Object(createItem(group.items))
    visibility.starts.seedItems(ValueSlot(JsonValue.Array(listOf(item)), Ref(0, listOf(id))))
    writeValue(key, ValueSlot(JsonValue.Array(items + item), Ref(freshToken(), values.itemIds(key) + id)))
    return true
}

private fun <V : Any> reindex(
    map: MutableStringMap<V>,
    key: String,
    place: (Int) -> Int?,
) {
    val moved = MutableStringMap<V>()
    for (errorKey in map.keys) {
        val (index, field) = splitPath(key, errorKey) ?: continue
        val target = place(index)
        if (target == index) continue
        if (target != null) map[errorKey]?.let { moved[itemPath(key, target, field)] = it }
        map.remove(errorKey)
    }
    map.merge(moved)
}

private fun EngineCore.reindexItems(
    key: String,
    place: (Int) -> Int?,
) {
    reindex(errors, key, place)
    reindex(serverErrors, key, place)
    reindex(fieldLoading, key, place)
}

internal fun EngineCore.removeItem(
    key: String,
    index: Int,
): Boolean {
    val items = itemsOf(values[key])
    val group = activeGroup(key) ?: return false
    if (index !in items.indices || items.size.toDouble() <= group.itemLimits.min) return false
    val ids = values.itemIds(key).toMutableList()
    visibility.starts.dropItem(values.itemId(key, index))
    reindexItems(key) { position -> if (position == index) null else position - (if (position > index) 1 else 0) }
    if (index < ids.size) ids.removeAt(index)
    writeValue(key, ValueSlot(JsonValue.Array(items.filterIndexed { position, _ -> position != index }), Ref(freshToken(), ids)))
    return true
}

private fun <T> MutableList<T>.move(
    from: Int,
    to: Int,
) {
    add(to, removeAt(from))
}

internal fun EngineCore.moveItem(
    key: String,
    from: Int,
    to: Int,
): Boolean {
    val items = itemsOf(values[key]).toMutableList()
    activeGroup(key) ?: return false
    if (from !in items.indices || to !in items.indices || from == to) return false
    val ids = values.itemIds(key).toMutableList()
    reindexItems(key) { position ->
        if (position == from) {
            to
        } else {
            val shifted = position - (if (position > from) 1 else 0)
            shifted + (if (shifted >= to) 1 else 0)
        }
    }
    items.move(from, to)
    ids.move(from, to)
    writeValue(key, ValueSlot(JsonValue.Array(items), Ref(freshToken(), ids)))
    return true
}

internal fun EngineCore.setItemValue(
    key: String,
    index: Int,
    fieldKey: String,
    input: JsonValue?,
) {
    val items = itemsOf(values[key])
    val group = activeGroup(key) ?: return
    val field = group.items.firstOrNull { it.key == fieldKey } ?: return
    val current = items.getOrNull(index) ?: return
    val item: JsonObject = current.objectValue ?: MutableJsonObject()
    val value = exclusiveWrite(field, item[fieldKey], input)
    val changed = !samePrimitive(item[fieldKey], value)
    var slot = values.slot(key)
    if (value != null &&
        current.objectValue != null
    ) {
        visibility.starts.rememberItem(values.itemId(key, index), fieldKey, value)
    }
    if (changed) {
        val updated = item.toMutableStringMap().also { it.assign(fieldKey, value) }
        val next = items.toMutableList().also { it[index] = JsonValue.Object(updated) }
        val ids = values.itemIds(key).toMutableList()
        if (current.objectValue == null && index < ids.size) ids[index] = freshToken()
        slot = ValueSlot(JsonValue.Array(next), Ref(freshToken(), ids))
    }
    clearError(itemPath(key, index, fieldKey), server = changed)
    writeValue(key, slot, own = false) { clearResolvedMatchErrors(fieldKey, ItemField(group, index, field)) }
}
