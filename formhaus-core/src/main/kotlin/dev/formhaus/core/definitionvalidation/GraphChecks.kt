package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.model.Conditional
import dev.formhaus.core.model.FormSection
import dev.formhaus.core.model.conditionFields
import dev.formhaus.core.model.valueFields

private fun nodeId(entry: FieldEntry): String = entry.group?.let { "${it.key}.${entry.field.key}" } ?: entry.field.key

private class DependencyGraph(private val context: CheckContext) {
    val keys = ArrayList<String>()
    val edges = HashMap<String, MutableList<String>>()

    init {
        for (entry in context.entries) link(listOf(nodeId(entry)), entry.field, entry.group?.key)
        for (entry in context.nodes) {
            val section = entry.node as? FormSection
            if (entry.group == null && section != null) link(section.fields.map { it.key }, section)
        }
        for (step in context.definition.steps.orEmpty()) link(valueFields(step.fields).map { it.key }, step)
    }

    private fun link(owners: List<String>, source: Conditional, group: String? = null) {
        val known = source.conditionFields.mapNotNull { key -> context.target(key, group)?.let(::nodeId) }
        if (known.isEmpty()) return
        for (owner in owners) edges.getOrPut(owner) { ArrayList<String>().also { keys.add(owner) } }.addAll(known)
    }
}

private class Frame(val node: String, var next: Int = 0)

private fun findCycles(graph: DependencyGraph): List<List<String>> {
    val visiting = HashMap<String, Boolean>()
    val cycles = ArrayList<List<String>>()
    for (start in graph.keys) {
        if (start in visiting) continue
        visiting[start] = true
        val stack = arrayListOf(Frame(start))
        while (stack.isNotEmpty()) {
            val frame = stack.last()
            val neighbors = graph.edges[frame.node].orEmpty()
            val position = frame.next++
            if (position >= neighbors.size) {
                visiting[frame.node] = false
                stack.removeLast()
                continue
            }
            val neighbor = neighbors[position]
            when (visiting[neighbor]) {
                true -> {
                    val nodes = stack.map { it.node }
                    val entry = nodes.indexOf(neighbor)
                    if (entry >= 0) cycles.add(nodes.subList(entry, nodes.size) + neighbor)
                }
                null -> {
                    visiting[neighbor] = true
                    stack.add(Frame(neighbor))
                }
                false -> Unit
            }
        }
    }
    return cycles
}

internal fun graphIssues(context: CheckContext): List<DefinitionIssue> {
    val paths = HashMap<String, String>()
    for (entry in context.entries) paths[nodeId(entry)] = entry.path
    return findCycles(DependencyGraph(context)).map { cycle ->
        val problem = "show conditions form a cycle: ${cycle.joinToString(" -> ")}"
        error(IssueCode.CONDITION_CYCLE, paths[cycle[0]].orEmpty(), problem, "Remove one of the conditions in the cycle")
    }
}
