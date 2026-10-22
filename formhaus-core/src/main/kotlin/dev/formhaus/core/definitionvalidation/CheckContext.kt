package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.model.Conditional
import dev.formhaus.core.model.FormAction
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.ShowCondition

internal class FieldEntry(
    val field: FormField,
    val path: String,
    val step: Int,
    val group: FormField?,
    val parent: FormNode?,
)

internal class NodeEntry(
    val node: FormNode,
    val path: String,
    val step: Int,
    val group: FormField?,
    val parent: FormNode?,
)

internal enum class ConditionOwner { FIELD, STEP, ROUTE, ACTION }

internal class ConditionEntry(
    val condition: ShowCondition,
    val path: String,
    val owner: ConditionOwner,
    val step: Int,
    val key: String?,
    val group: String?,
)

internal class ScopedEntries {
    val keys = ArrayList<String>()
    private val entries = HashMap<String, FieldEntry>()

    operator fun get(key: String): FieldEntry? = entries[key]

    fun addFirst(entry: FieldEntry) {
        val key = entry.field.key
        if (key in entries) return
        entries[key] = entry
        keys.add(key)
    }
}

private class ConditionSite(
    val path: String,
    val owner: ConditionOwner,
    val step: Int,
    val key: String? = null,
    val group: String? = null,
) {
    fun entries(lists: List<Pair<String, List<ShowCondition>?>>): List<ConditionEntry> =
        lists.flatMap { (name, conditions) ->
            conditions.orEmpty().mapIndexed {
                index,
                condition,
                ->
                ConditionEntry(condition, "$path/$name/$index", owner, step, key, group)
            }
        }
}

private fun showLists(source: Conditional) = listOf("show" to source.show, "showAny" to source.showAny)

private fun disabledLists(action: FormAction?) = listOf("disabled" to action?.disabled)

private fun nodeEntries(definition: FormDefinition): List<NodeEntry> {
    val nodes = ArrayList<NodeEntry>()

    fun visit(
        items: List<FormNode>,
        path: String,
        step: Int,
        parent: FormNode?,
        group: FormField?,
    ) {
        for ((position, node) in items.withIndex()) {
            val at = "$path/$position"
            nodes.add(NodeEntry(node, at, step, group, parent))
            val children = node.children
            if ((node.type == "section" || node.type == "group") && nestable(at) && children != null) {
                visit(children, "$at/fields", step, node, if ((node as? FormField)?.isGroup == true) node else group)
            }
        }
    }
    val steps = definition.steps.orEmpty()
    if (steps.isNotEmpty()) {
        for ((index, step) in steps.withIndex()) visit(step.fields, "/steps/$index/fields", index, null, null)
    } else {
        visit(definition.fields.orEmpty(), "/fields", -1, null, null)
    }
    return nodes
}

private fun conditionEntries(
    definition: FormDefinition,
    nodes: List<NodeEntry>,
): List<ConditionEntry> {
    val entries = ArrayList<ConditionEntry>()
    for (entry in nodes) {
        entries +=
            ConditionSite(
                entry.path,
                ConditionOwner.FIELD,
                entry.step,
                entry.node.key,
                entry.group?.key,
            ).entries(showLists(entry.node))
    }
    for ((index, step) in definition.steps.orEmpty().withIndex()) {
        entries += ConditionSite("/steps/$index", ConditionOwner.STEP, index).entries(showLists(step))
        for ((position, route) in step.routes.orEmpty().withIndex()) {
            entries +=
                ConditionSite("/steps/$index/routes/$position", ConditionOwner.ROUTE, index).entries(showLists(route))
        }
        for (name in listOf("next", "back", "skip")) {
            entries +=
                ConditionSite(
                    "/steps/$index/$name",
                    ConditionOwner.ACTION,
                    index,
                ).entries(disabledLists(step.button(name).action))
        }
    }
    val submit = definition.json["submit"]?.objectValue?.let(::FormAction)
    entries += ConditionSite("/submit", ConditionOwner.ACTION, -1).entries(disabledLists(submit))
    entries += ConditionSite("/cancel", ConditionOwner.ACTION, -1).entries(disabledLists(definition.cancel))
    return entries
}

internal class CheckContext(
    val definition: FormDefinition,
    val validators: Set<String>? = null,
) {
    val nodes: List<NodeEntry> = nodeEntries(definition)
    val entries: List<FieldEntry> =
        nodes.mapNotNull { entry ->
            (entry.node as? FormField)?.let { FieldEntry(it, entry.path, entry.step, entry.group, entry.parent) }
        }
    val keys = ScopedEntries()
    val scopes = HashMap<String, ScopedEntries>()
    val scopeOrder = ArrayList<String>()
    val conditions: List<ConditionEntry> = conditionEntries(definition, nodes)

    init {
        for (entry in entries) {
            val group = entry.group
            if (group == null) {
                keys.addFirst(entry)
                continue
            }
            val scope = scopes.getOrPut(group.key) { ScopedEntries().also { scopeOrder.add(group.key) } }
            scope.addFirst(entry)
        }
    }

    val keyList: List<String> get() = keys.keys

    fun target(
        key: String,
        group: String? = null,
    ): FieldEntry? = group?.let { scopes[it]?.get(key) } ?: keys[key]
}
