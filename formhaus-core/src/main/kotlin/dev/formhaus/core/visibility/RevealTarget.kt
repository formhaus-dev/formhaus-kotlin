package dev.formhaus.core.visibility

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.strictEquals
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.conditionOperators

public data class RevealTarget(
    public val parent: Int,
    public val option: Int,
)

private fun revealKey(value: JsonValue?): Any? =
    when (value) {
        is JsonValue.String -> value.value
        is JsonValue.Bool -> value.value
        is JsonValue.Number -> if (value.value == 0.0) 0.0 else value.value
        JsonValue.Null -> JsonValue.Null
        else -> null
    }

private class RevealParent(
    val field: FormField,
    val operatorName: String,
    options: List<JsonValue>,
) {
    val multiple = field.type == "multiselect"
    val randomize = field.randomize
    private val positions = HashMap<Any, Int>(options.size * 2)

    init {
        options.forEachIndexed { position, option -> revealKey(option["value"])?.let { positions.putIfAbsent(it, position) } }
    }

    fun follows(
        option: Int,
        last: Int,
    ): Boolean =
        when {
            !multiple || last < 0 -> true
            randomize -> option == last
            else -> option >= last
        }

    fun option(node: FormNode): Int? {
        if (node !is FormField || node.type == "group" || node.type == "hidden") return null
        val conditions = node.json["show"]?.arrayValue.orEmpty() + node.json["showAny"]?.arrayValue.orEmpty()
        val condition = conditions.singleOrNull()?.objectValue ?: return null
        if (!strictEquals(condition["field"], field.json["key"])) return null
        if (conditionOperators.firstOrNull { it in condition } != operatorName) return null
        return revealKey(condition[operatorName])?.let(positions::get)
    }

    companion object {
        fun of(node: FormNode): RevealParent? {
            val field = node as? FormField ?: return null
            val operatorName = operators[field.type] ?: return null
            val options = field.json["options"]?.arrayValue
            val eligible =
                !options.isNullOrEmpty() &&
                    !field.hasOptionsFrom &&
                    field.layout != JsonValue.String("inline") &&
                    options.none { it["image"]?.isTruthy == true }
            return if (eligible) RevealParent(field, operatorName, options.orEmpty()) else null
        }
    }
}

private val operators = mapOf("radio" to "eq", "multiselect" to "contains")

public fun revealTargets(nodes: List<FormNode>): List<RevealTarget?> {
    val targets = arrayOfNulls<RevealTarget>(nodes.size)
    nodes.forEachIndexed { index, node ->
        val parent = RevealParent.of(node) ?: return@forEachIndexed
        var last = -1
        for (at in index + 1 until nodes.size) {
            val option = parent.option(nodes[at]) ?: break
            if (!parent.follows(option, last)) break
            if (targets[at] == null) targets[at] = RevealTarget(index, option)
            last = option
        }
    }
    return targets.asList()
}
