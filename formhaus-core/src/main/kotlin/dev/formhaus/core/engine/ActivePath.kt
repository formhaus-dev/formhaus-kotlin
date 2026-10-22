package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.FormStep
import dev.formhaus.core.model.valueFields
import dev.formhaus.core.validation.trimValue
import dev.formhaus.core.visibility.ListLimits
import dev.formhaus.core.visibility.cleanItem
import dev.formhaus.core.visibility.cleanItems
import dev.formhaus.core.visibility.isVisible
import dev.formhaus.core.visibility.shownFields

private fun cachedItems(group: FormField, value: JsonValue?, scope: JsonObject, limits: ListLimits, cache: ItemCache<JsonValue>?): JsonValue? {
    val items = value?.arrayValue
    if (cache == null || items == null) return cleanItems(group, value, scope, limits)
    val fields = group.items
    val context = cache.context(group, scope, limits)
    return JsonValue.Array(cache.map(group, items, context = context) { _, item -> Mapped(cleanItem(fields, item, scope, limits), true) }.items)
}

private class Available(val values: MutableJsonObject = MutableJsonObject(), var limits: ListLimits = ListLimits.STANDARD)

private fun addStepValues(nodes: List<FormNode>, values: JsonObject, target: Available, cache: ItemCache<JsonValue>?) {
    val fields = valueFields(nodes)
    target.limits = target.limits.trimmed(fields)
    val limits = target.limits
    val available = target.values
    val saved = fields.map { it.key to available[it.key] }
    for (field in fields) available.assign(field.key, trimValue(field, values[field.key]))
    val shown = shownFields(nodes, available, limits).filter { values[it.key] != null }.map { field ->
        field.key to if (field.isGroup) cachedItems(field, values[field.key], available, limits, cache) else trimValue(field, values[field.key])
    }
    for ((key, value) in saved.asReversed()) available.assign(key, value)
    for ((key, value) in shown) available.assign(key, value)
}

internal class ActivePath(val steps: List<FormStep>, val values: JsonObject, val limits: ListLimits) {
    private val ordinals = steps.mapTo(HashSet()) { it.ordinal }

    fun ordered(definition: FormDefinition): JsonObject {
        val fields = if (definition.isMultiStep) steps.flatMap { valueFields(it.fields) } else valueFields(definition.fields.orEmpty())
        val sorted = MutableJsonObject()
        for (field in fields) {
            if (field.key in sorted) continue
            values[field.key]?.let { sorted[field.key] = it }
        }
        return sorted
    }

    operator fun contains(step: FormStep): Boolean = step.ordinal in ordinals

    companion object {
        val EMPTY: ActivePath = ActivePath(emptyList(), MutableJsonObject(), ListLimits.STANDARD)

        fun walk(definition: FormDefinition, values: JsonObject, cache: ItemCache<JsonValue>? = null): ActivePath {
            val steps = definition.steps.orEmpty()
            val indexes = stepIndexes(steps)
            val result = ArrayList<FormStep>()
            val available = Available()
            if (steps.isEmpty()) addStepValues(definition.fields.orEmpty(), values, available, cache)
            var index = 0
            while (index < steps.size) {
                val step = steps[index]
                if (!isVisible(step, available.values, available.limits)) {
                    index += 1
                    continue
                }
                result.add(step)
                addStepValues(step.fields, values, available, cache)
                val route = step.routes?.firstOrNull { route ->
                    if (!isVisible(route, available.values, available.limits)) return@firstOrNull false
                    if (route.endsForm) return@firstOrNull true
                    val target = route.to?.let(indexes::get) ?: return@firstOrNull false
                    target > index && isVisible(steps[target], available.values, available.limits)
                }
                if (route?.endsForm == true) break
                index = route?.to?.let(indexes::get) ?: (index + 1)
            }
            return ActivePath(result, available.values, available.limits)
        }
    }
}

internal class PathCache(private val definition: FormDefinition) {
    private var source: JsonObject? = null
    private var path = ActivePath.EMPTY
    private var ordered: JsonObject? = null
    private val items = ItemCache<JsonValue>()

    fun path(values: JsonObject): ActivePath {
        if (source != values) {
            path = ActivePath.walk(definition, values, items)
            ordered = null
        }
        source = values
        return path
    }

    fun conditionValues(values: JsonObject): JsonObject {
        val path = path(values)
        return ordered ?: path.ordered(definition).also { ordered = it }
    }
}

internal fun stepIndexes(steps: List<FormStep>): Map<String, Int> = steps.withIndex().associate { (index, step) -> step.id to index }

internal fun activeSteps(definition: FormDefinition, values: JsonObject): List<FormStep> = ActivePath.walk(definition, values).steps

internal fun reconcileStepIndex(previous: List<FormStep>, next: List<FormStep>, index: Int): Int {
    val previousStep = previous.getOrNull(index) ?: previous.lastOrNull()
    if (previousStep != null) {
        val preserved = next.indexOfFirst { it.id == previousStep.id }
        if (preserved >= 0) return preserved
    }
    var before = minOf(index - 1, previous.size - 1)
    while (before >= 0) {
        val predecessor = next.indexOfFirst { it.id == previous[before].id }
        if (predecessor >= 0) return predecessor
        before -= 1
    }
    return 0
}
