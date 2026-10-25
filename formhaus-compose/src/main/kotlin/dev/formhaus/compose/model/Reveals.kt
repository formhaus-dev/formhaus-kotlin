package dev.formhaus.compose.model

import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.FormSection
import dev.formhaus.core.visibility.revealTargets

internal class RevealPlan(
    val definition: FormDefinition,
) {
    private val parents = HashMap<String, Pair<String, Int>>()
    private val options = HashMap<String, MutableSet<Int>>()

    init {
        val tops = definition.steps?.takeIf { it.isNotEmpty() }?.map { it.fields } ?: listOf(definition.fields.orEmpty())
        val sections = tops.flatMap { nodes -> nodes.filterIsInstance<FormSection>().filterNot { it.isMatrix } }
        for (nodes in tops + sections.map { it.fields }) {
            revealTargets(nodes).forEachIndexed { index, target ->
                if (target == null) return@forEachIndexed
                val parent = nodes[target.parent].key
                parents[nodes[index].key] = parent to target.option
                options.getOrPut(parent) { sortedSetOf() }.add(target.option)
            }
        }
    }

    fun layout(
        nodes: List<FormNode>,
        draws: (FormField) -> Boolean,
    ): RevealLayout {
        val slots = LinkedHashMap<String, Map<Int, MutableList<FormNode>>>()
        for (node in nodes) {
            val indexes = (node as? FormField)?.let { options[it.key] } ?: continue
            if (draws(node)) slots[node.key] = indexes.associateWith { mutableListOf() }
        }
        val top = ArrayList<FormNode>(nodes.size)
        for (node in nodes) {
            val target = if (node is FormField) parents[node.key] else null
            val slot = target?.let { (parent, option) -> slots[parent]?.get(option) }
            if (slot != null) slot += node else top += node
        }
        val fields = nodes.filterIsInstance<FormField>().associateBy { it.key }
        return RevealLayout(
            top,
            slots.mapValues { (parent, byOption) ->
                val choices = fields.getValue(parent).options.orEmpty()
                byOption.entries.associate { (option, children) -> choices[option].value.orEmpty() to children.toList() }
            },
        )
    }
}

internal class RevealLayout(
    val nodes: List<FormNode>,
    private val slots: Map<String, Map<String, List<FormNode>>>,
) {
    fun slots(node: FormNode): Map<String, List<FormNode>>? = if (node is FormField) slots[node.key] else null

    fun nestedKeys(node: FormNode): List<String> =
        slots(node)
            .orEmpty()
            .values
            .flatMap { children -> children.flatMap { listOf(it.key) + nestedKeys(it) } }
}

internal fun FormModel.revealLayout(
    nodes: List<FormNode>,
    draws: ((FormField) -> Boolean)?,
): RevealLayout {
    if (draws == null) return RevealLayout(nodes, emptyMap())
    val plan = revealPlan?.takeIf { it.definition === definition } ?: RevealPlan(definition).also { revealPlan = it }
    return plan.layout(nodes, draws)
}
