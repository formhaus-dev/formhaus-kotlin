package dev.formhaus.core.engine

import dev.formhaus.core.json.FieldErrors
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.FormSection
import dev.formhaus.core.model.FormStep
import dev.formhaus.core.model.isConditioned
import dev.formhaus.core.model.valueFields
import dev.formhaus.core.regex.CallBudget
import dev.formhaus.core.validation.ShownChecks
import dev.formhaus.core.validation.Validators
import dev.formhaus.core.validation.customRules
import dev.formhaus.core.validation.validateShown
import dev.formhaus.core.visibility.ListLimits
import dev.formhaus.core.visibility.isVisible
import dev.formhaus.core.visibility.itemScope
import dev.formhaus.core.visibility.itemsOf
import dev.formhaus.core.visibility.shownFields
import dev.formhaus.core.visibility.splitPath

internal class ItemField(
    val group: FormField,
    val index: Int,
    val field: FormField,
)

internal class ParsedPath(
    val group: String,
    val index: Int,
    val field: String,
)

internal class VisibilityView(
    val visibleSteps: List<FormStep> = emptyList(),
    val currentStep: FormStep? = null,
    val visibleFields: List<FormField> = emptyList(),
    val visibleNodes: List<FormNode> = emptyList(),
)

internal class VisibilityState(
    val definition: FormDefinition,
    val keep: Boolean = false,
) {
    val index = DefinitionIndex(definition)
    val groups: List<FormField> = index.allFields.filter { it.isGroup }
    val paths = PathCache(definition)
    val reconciled = ItemCache<JsonValue>()
    val listed = ItemCache<GroupItem>()
    val filled = ItemCache<Boolean>()
    val shown = ShownCache(index, groups)
    val conditional: List<FormField> =
        index.allFields.filter {
            it.isConditioned ||
                index.section(it)?.isConditioned == true
        }
    private val customSteps: Set<Int> =
        definition.steps
            .orEmpty()
            .filter {
                customRules(it.fields)
            }.mapTo(HashSet()) { it.ordinal }
    val order: List<String> = orderedKeys(index.fieldDependents.keys + index.stepDependents.keys)
    val starts = StartValues()
    var settled = false
    var waiting = HashSet<String>()
    private var visibilityDirty = true
    private var canGoNextDirty = true
    private var view = VisibilityView()
    private var canGoNext = false

    val allFields: List<FormField> get() = index.allFields

    fun scope(values: JsonObject): JsonObject = paths.path(values).values

    fun conditionValues(values: JsonObject): JsonObject = paths.conditionValues(values)

    fun limits(values: JsonObject): ListLimits = paths.path(values).limits

    fun visible(
        field: FormField,
        values: JsonObject,
    ): Boolean {
        val active = scope(values)
        val limits = limits(values)
        val section = index.section(field)
        return (section == null || isVisible(section, active, limits)) && isVisible(field, active, limits)
    }

    fun onPath(
        field: FormField,
        values: JsonObject,
    ): Boolean {
        val step = index.step(field) ?: return true
        return step in paths.path(values)
    }

    fun activeField(
        key: String,
        values: JsonObject,
    ): FormField? = index.fieldsByKey[key]?.firstOrNull { onPath(it, values) && visible(it, values) }

    fun fieldOf(
        key: String,
        values: JsonObject,
    ): FormField? = activeField(key, values) ?: index.fieldsByKey[key]?.firstOrNull()

    fun parse(key: String): ParsedPath? {
        if (index.fieldsByKey.has(key)) return null
        for (group in groups) {
            val (position, field) = splitPath(group.key, key) ?: continue
            return ParsedPath(group.key, position, field)
        }
        return null
    }

    fun owner(key: String): String = parse(key)?.group ?: key

    fun activeItem(
        key: String,
        values: JsonObject,
    ): ItemField? {
        val path = parse(key) ?: return null
        val group = activeField(path.group, values)?.takeIf { it.isGroup } ?: return null
        val item = itemsOf(values[path.group]).getOrNull(path.index) ?: return null
        val inner = itemScope(group, item, scope(values))
        val field =
            group.items.firstOrNull { it.key == path.field && isVisible(it, inner, limits(values)) } ?: return null
        return ItemField(group, path.index, field)
    }

    fun state(
        values: JsonObject,
        stepIndex: Int,
    ): VisibilityView {
        if (!visibilityDirty) return view
        visibilityDirty = false
        val multi = definition.isMultiStep
        val steps = if (multi) paths.path(values).steps else emptyList()
        val current = if (multi) steps.getOrNull(stepIndex) ?: steps.lastOrNull() else null
        val source = if (multi) current?.fields.orEmpty() else definition.fields.orEmpty()
        val active = scope(values)
        val limits = limits(values)
        view = VisibilityView(steps, current, shownFields(source, active, limits), shownNodes(source, active, limits))
        return view
    }

    fun shownNodes(
        source: List<FormNode>,
        values: JsonObject,
    ): List<FormNode> = shownNodes(source, scope(values), limits(values))

    private fun shownNodes(
        source: List<FormNode>,
        active: JsonObject,
        limits: ListLimits,
    ): List<FormNode> =
        source.filter { isVisible(it, active, limits) }.map { node ->
            if (node is FormSection) node.filtered { isVisible(it, active, limits) } else node
        }

    fun markChanged(
        structureChanged: Boolean,
        valuesChanged: Boolean,
    ) {
        if (structureChanged) visibilityDirty = true
        if (structureChanged || valuesChanged) canGoNextDirty = true
    }

    fun canGoNext(
        values: JsonObject,
        stepIndex: Int,
        validators: Validators,
    ): Boolean {
        val step = state(values, stepIndex).currentStep
        if (!canGoNextDirty) return canGoNext
        canGoNextDirty = false
        if (step != null && step.ordinal in customSteps) settled = false
        val scope = conditionValues(values)
        val checks = ShownChecks(validators, null, limits(values))
        val errors: FieldErrors =
            step?.let { CallBudget.run { validateShown(it.fields, scope, scope, checks) } } ?: MutableStringMap()
        canGoNext = definition.isMultiStep && errors.isEmpty()
        return canGoNext
    }

    fun findStepIndex(
        fieldKey: String,
        values: JsonObject,
        stepIndex: Int,
    ): Int? {
        val key = owner(fieldKey)
        return state(values, stepIndex)
            .visibleSteps
            .indexOfFirst { step ->
                valueFields(step.fields).any { it.key == key }
            }.takeIf {
                it >=
                    0
            }
    }

    fun affectsStructure(
        key: String,
        cleared: Set<String>,
    ): Boolean =
        key in index.routeFields || index.fieldDependents.has(key) || index.stepDependents.has(key) ||
            cleared.isNotEmpty()

    fun unsettle() {
        settled = false
    }
}
