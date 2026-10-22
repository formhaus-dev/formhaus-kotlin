package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.model.Conditional
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormSection
import dev.formhaus.core.model.FormStep

internal val reservedKeys =
    setOf(
        "constructor",
        "__defineGetter__",
        "__defineSetter__",
        "hasOwnProperty",
        "__lookupGetter__",
        "__lookupSetter__",
        "isPrototypeOf",
        "propertyIsEnumerable",
        "toString",
        "valueOf",
        "__proto__",
        "toLocaleString",
        "prototype",
    )

private fun isConditional(source: Conditional): Boolean = !source.show.isNullOrEmpty() || !source.showAny.isNullOrEmpty()

private fun successors(
    steps: List<FormStep>,
    indexes: Map<String, Int>,
    from: Int,
): List<Int> {
    val next = ArrayList<Int>()
    for (route in steps[from].routes.orEmpty()) {
        if (route.endsForm && !isConditional(route)) return next
        val target = route.to?.let { indexes[it] }
        if (route.endsForm || target == null || target <= from) continue
        next.add(target)
        if (!isConditional(route) && !isConditional(steps[target])) return next
    }
    if (from + 1 < steps.size) next.add(from + 1)
    return next
}

internal fun stepIndexes(steps: List<FormStep>): Map<String, Int> {
    val indexes = HashMap<String, Int>()
    for ((index, step) in steps.withIndex()) indexes[step.id] = index
    return indexes
}

private class StepReachability(
    private val steps: List<FormStep>,
) {
    private val indexes = stepIndexes(steps)
    private val memo = HashMap<Int, Set<Int>>()

    fun reaches(
        start: Int,
        target: Int,
    ): Boolean {
        if (start !in steps.indices) return false
        memo[start]?.let { return target in it }
        val seen = HashSet<Int>()
        val queue = ArrayList(successors(steps, indexes, start))
        while (queue.isNotEmpty()) {
            val index = queue.removeAt(queue.lastIndex)
            if (!seen.add(index)) continue
            queue.addAll(successors(steps, indexes, index))
            if (isConditional(steps[index]) && index + 1 < steps.size) queue.add(index + 1)
        }
        memo[start] = seen
        return target in seen
    }
}

private fun stepIssues(context: CheckContext): List<DefinitionIssue> {
    val issues = ArrayList<DefinitionIssue>()
    val steps = context.definition.steps.orEmpty()
    val seen = HashMap<String, Int>()
    for ((index, step) in steps.withIndex()) {
        val first = seen[step.id]
        if (first == null) {
            seen[step.id] = index
            continue
        }
        val problem = "step id \"${step.id}\" is already used by /steps/$first"
        issues.add(error(IssueCode.DUPLICATE_STEP_ID, "/steps/$index/id", problem, "Give each step a unique id"))
    }
    for ((index, step) in steps.withIndex()) {
        for ((position, route) in step.routes.orEmpty().withIndex()) {
            if (route.endsForm) continue
            val to = route.to ?: ""
            val target = seen[to]
            if (target != null && target > index) continue
            val later = steps.drop(index + 1).map { it.id }
            val problem = if (target == null) "\"$to\" is not a step id" else "\"$to\" is not a later step"
            val hint =
                if (later.isEmpty()) {
                    "Use null to end the form here"
                } else {
                    "${oneOf(
                        later,
                    )}, or null to end the form here"
                }
            issues.add(error(IssueCode.ROUTE_TARGET, "/steps/$index/routes/$position/to", problem, hint))
        }
    }
    return issues
}

private fun keyClashes(context: CheckContext): List<NodeEntry?> {
    val reachable = StepReachability(context.definition.steps.orEmpty())
    val exclusive = { a: NodeEntry, b: NodeEntry -> a.step != b.step && !reachable.reaches(a.step, b.step) }
    val firstOuter = HashMap<String, NodeEntry>()
    for (entry in context.nodes) if (entry.group == null) firstOuter.putIfAbsent(entry.node.key, entry)
    val earlierOuter = HashMap<String, MutableList<NodeEntry>>()
    val earlierInner = HashMap<Int, HashMap<String, NodeEntry>>()
    return context.nodes.map { entry ->
        val key = entry.node.key
        val group = entry.group
        if (group == null) {
            val clash = earlierOuter[key]?.firstOrNull { !exclusive(it, entry) }
            earlierOuter.getOrPut(key) { ArrayList() }.add(entry)
            clash
        } else {
            val inner = earlierInner.getOrPut(group.ordinal) { HashMap() }
            inner[key] ?: firstOuter[key].also { inner[key] = entry }
        }
    }
}

private fun keyIssue(
    entry: NodeEntry,
    clash: NodeEntry?,
    prefix: NodeEntry?,
): DefinitionIssue? {
    val key = entry.node.key
    val path = "${entry.path}/key"
    if (key in
        reservedKeys
    ) {
        return error(
            IssueCode.RESERVED_KEY,
            path,
            "\"$key\" is a built-in object property name",
            "Rename the field key",
        )
    }
    if (clash != null) {
        val inGroup = entry.group != null
        val problem = "key \"$key\" is already used by ${clash.path}${if (inGroup) "" else " on the same path"}"
        val hint = if (inGroup) "Rename the field" else "Rename one of the fields, or move them to route branches that never meet"
        return error(IssueCode.DUPLICATE_KEY, path, problem, hint)
    }
    prefix ?: return null
    return error(
        IssueCode.DUPLICATE_KEY,
        path,
        "key \"$key\" starts with the group key \"${prefix.node.key}.\"",
        "Rename the field",
    )
}

private fun keyIssues(context: CheckContext): List<DefinitionIssue> {
    val groups = context.nodes.filter { it.group == null && (it.node as? FormField)?.isGroup == true }
    return context.nodes.zip(keyClashes(context)).mapNotNull { (entry, clash) ->
        val prefix =
            if (entry.group ==
                null
            ) {
                groups.firstOrNull { entry.node.key.startsWith("${it.node.key}.") }
            } else {
                null
            }
        keyIssue(entry, clash, prefix)
    }
}

private val groupedTypes = listOf("group", "file", "hidden")

private fun nestingIssues(context: CheckContext): List<DefinitionIssue> =
    context.nodes.mapNotNull { entry ->
        val parent = entry.parent ?: return@mapNotNull null
        if (entry.node !is FormSection &&
            !(entry.group != null && entry.node.type in groupedTypes)
        ) {
            return@mapNotNull null
        }
        error(
            IssueCode.INVALID_NESTING,
            "${entry.path}/type",
            "a ${entry.node.type} cannot be inside a ${parent.type}",
            "Move it to the fields of the step",
        )
    }

internal fun structureIssues(context: CheckContext): List<DefinitionIssue> {
    val definition = context.definition
    val mixed =
        if (!definition.fields.isNullOrEmpty() && !definition.steps.isNullOrEmpty()) {
            val hint = "Move the top-level fields into a step, or remove \"steps\""
            listOf(error(IssueCode.FIELDS_AND_STEPS, "", "the definition has both \"fields\" and \"steps\"", hint))
        } else {
            emptyList()
        }
    return mixed + stepIssues(context) + keyIssues(context) + nestingIssues(context)
}
