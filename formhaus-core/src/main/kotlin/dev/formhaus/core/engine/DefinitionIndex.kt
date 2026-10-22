package dev.formhaus.core.engine

import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormSection
import dev.formhaus.core.model.FormStep
import dev.formhaus.core.model.conditionFields
import dev.formhaus.core.model.valueFields

internal class OrderedIndex<V>(private val ordinal: (V) -> Int) {
    private val storage = LinkedHashMap<String, MutableList<V>>()

    val keys: List<String> get() = storage.keys.toList()

    operator fun get(key: String): List<V>? = storage[key]

    fun has(key: String): Boolean = key in storage

    fun insert(value: V, key: String) {
        val list = storage.getOrPut(key) { ArrayList() }
        if (list.none { ordinal(it) == ordinal(value) }) list.add(value)
    }
}

internal class DefinitionIndex(definition: FormDefinition) {
    val allFields: List<FormField> = definition.allFields
    val fieldsByKey = OrderedIndex<FormField> { it.ordinal }
    val routeFields = HashSet<String>()
    val fieldDependents = OrderedIndex<FormField> { it.ordinal }
    val stepDependents = OrderedIndex<FormStep> { it.ordinal }
    private val stepOfField = HashMap<Int, FormStep>()
    private val sectionOfField = HashMap<Int, FormSection>()

    init {
        for (section in definition.topNodes.filterIsInstance<FormSection>()) {
            for (field in section.fields.filterIsInstance<FormField>()) sectionOfField[field.ordinal] = section
        }
        for (field in allFields) indexField(field)
        for (step in definition.steps.orEmpty()) indexStep(step)
    }

    private fun indexField(field: FormField) {
        fieldsByKey.insert(field, field.key)
        val sources = field.conditionFields + section(field)?.conditionFields.orEmpty() + field.items.flatMap { it.conditionFields }
        for (key in sources) fieldDependents.insert(field, key)
    }

    private fun indexStep(step: FormStep) {
        for (field in valueFields(step.fields)) stepOfField[field.ordinal] = step
        for (key in step.conditionFields) stepDependents.insert(step, key)
        for (route in step.routes.orEmpty()) routeFields.addAll(route.conditionFields)
    }

    fun step(field: FormField): FormStep? = stepOfField[field.ordinal]

    fun section(field: FormField): FormSection? = sectionOfField[field.ordinal]

    fun conditionSources(field: FormField): List<String> =
        field.conditionFields + section(field)?.conditionFields.orEmpty() + step(field)?.conditionFields.orEmpty() +
            field.items.flatMap { it.conditionFields }

    fun isShared(key: String): Boolean = (fieldsByKey[key]?.size ?: 0) > 1
}

internal fun orderedKeys(items: List<String>): List<String> = LinkedHashSet(items).toList()
