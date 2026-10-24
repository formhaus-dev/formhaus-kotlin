package dev.formhaus.core.corpus

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormSection
import dev.formhaus.core.visibility.revealTargets

fun revealPointers(json: JsonValue): JsonValue {
    val definition = FormDefinition(json.objectValue ?: MutableJsonObject())
    val tops =
        if (definition.isMultiStep) {
            definition.steps.orEmpty().mapIndexed { index, step -> "/steps/$index/fields" to step.fields }
        } else {
            listOf("/fields" to definition.fields.orEmpty())
        }
    val lists =
        tops.flatMap { (path, nodes) ->
            listOf(path to nodes) +
                nodes.mapIndexedNotNull { index, node ->
                    (node as? FormSection)?.takeUnless { it.isMatrix }?.let { "$path/$index/fields" to it.fields }
                }
        }
    val found = MutableJsonObject()
    for ((path, nodes) in lists) {
        revealTargets(nodes).forEachIndexed { index, target ->
            if (target == null) return@forEachIndexed
            val entry = MutableJsonObject()
            entry["parent"] = JsonValue.String("$path/${target.parent}")
            entry["option"] = JsonValue.Number(target.option)
            found["$path/$index"] = JsonValue.Object(entry)
        }
    }
    return JsonValue.Object(found)
}
