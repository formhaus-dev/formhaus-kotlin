package dev.formhaus.core.engine

import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.valueFields
import dev.formhaus.core.visibility.isVisible

private class HiddenPass(
    val visibility: VisibilityState,
    val values: Values,
    val errors: MutableStringMap<String>,
    val fields: Set<Int>?,
    keys: List<String>,
) {
    val queue = ArrayList(keys)
    val queued = HashSet(keys)
    val cleared = HashSet<String>()

    val index: DefinitionIndex get() = visibility.index

    fun held(key: String): Boolean = values[key] != null || errors[key] != null

    fun mark(key: String) {
        cleared.add(key)
        val next = if (key in index.routeFields) visibility.order + key else listOf(key)
        for (item in next) if (queued.add(item)) queue.add(item)
    }

    fun clear(key: String) {
        visibility.starts.remember(key, values.slot(key))
        values.remove(key)
        errors.remove(key)
        if (visibility.groups.any { it.key == key }) {
            for (errorKey in errors.keys) if (visibility.owner(errorKey) == key) errors.remove(errorKey)
        }
        mark(key)
    }

    fun update(field: FormField, shown: Boolean, dry: Boolean): Boolean {
        val key = field.key
        if (dry) {
            visibility.waiting.remove(key)
            if (values[key] == null && errors[key] != null) visibility.waiting.add(key)
        }
        val shared = index.isShared(key)
        val active = if (shared) visibility.activeField(key, values.json) else null
        if (active != null && active.ordinal != field.ordinal) return false
        if (!shown) {
            val hidden = held(key)
            if (hidden && !dry) clear(key)
            return hidden
        }
        val eligible = if (shared) active != null else visibility.onPath(field, values.json) && visibility.visible(field, values.json)
        if (held(key) || !eligible) return false
        val start = visibility.starts.startValue(field)
        if (start.value == null) return false
        if (dry) return true
        values.set(key, start)
        visibility.starts.seedItems(values.slot(key))
        mark(key)
        return true
    }

    fun included(field: FormField): Boolean = fields?.contains(field.ordinal) ?: true

    fun visit(key: String, dry: Boolean): Boolean {
        var acted = false
        for (field in index.fieldDependents[key].orEmpty()) {
            if (!included(field) || !visibility.onPath(field, values.json)) continue
            acted = update(field, visibility.visible(field, values.json), dry) || acted
        }
        for (step in index.stepDependents[key].orEmpty()) {
            for (field in valueFields(step.fields)) {
                if (!included(field)) continue
                val shown = isVisible(step, visibility.scope(values.json), visibility.limits(values.json))
                acted = update(field, shown, dry) || acted
            }
        }
        return acted
    }
}

private fun VisibilityState.downstream(key: String): Set<Int>? {
    val fields = HashSet<Int>()
    val keys = arrayListOf(key)
    val seen = hashSetOf(key)
    var position = 0
    while (position < keys.size) {
        val next = keys[position]
        position += 1
        if (next in index.routeFields || index.stepDependents.has(next)) return null
        for (field in index.fieldsByKey[next].orEmpty()) fields.add(field.ordinal)
        for (field in index.fieldDependents[next].orEmpty()) if (seen.add(field.key)) keys.add(field.key)
    }
    return fields
}

internal fun VisibilityState.reconcileHidden(
    values: Values,
    errors: MutableStringMap<String>,
    initial: Boolean = false,
    written: String? = null,
): Set<String> {
    if (initial) starts.resetStarts(values, groups)
    val incremental = !initial && written != null && settled && waiting.all { values[it] != null || errors[it] != null }
    val fields = if (incremental) downstream(written) else null
    var keys = order
    if (fields != null) {
        val position = HashMap<String, Int>()
        order.forEachIndexed { index, key -> position.putIfAbsent(key, index) }
        val sources = allFields.filter { it.ordinal in fields }.flatMap(index::conditionSources)
        keys = orderedKeys(sources).sortedBy { position[it] ?: Int.MAX_VALUE }
    }
    val pass = HiddenPass(this, values, errors, fields, keys)
    var cursor = 0
    while (cursor < pass.queue.size) {
        val key = pass.queue[cursor]
        pass.queued.remove(key)
        pass.visit(key, dry = false)
        cursor += 1
    }
    if (groups.isNotEmpty()) reconcileItems(values, errors, pass.cleared)
    if (fields == null) waiting = HashSet()
    settled = true
    if (keys.any { pass.visit(it, dry = true) }) settled = false
    return pass.cleared
}
