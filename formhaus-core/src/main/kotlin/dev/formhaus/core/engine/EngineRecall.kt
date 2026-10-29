package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormField
import dev.formhaus.core.validation.hasRecallBraces

internal fun FormEngine.recallScope(): RecallScope {
    val visibility = core.visibility
    val values = core.values.json
    return RecallScope(visibility, core.getSubmitValues(), options.lang, options.recallFormat, storedFileName) {
        visibility.activeField(it, values)
    }
}

private fun FormEngine.recalls(json: JsonObject): Boolean =
    definition.recallsAnswers && recallSlots.any { slot -> json[slot]?.stringValue?.let(::hasRecallBraces) == true }

internal fun FormEngine.recalledSlots(
    json: JsonObject,
    item: RecallItem?,
): JsonObject {
    if (!recalls(json)) return json
    val resolve = recallScope().resolver(RecallOptions(item))
    val result = json.toMutableStringMap()
    for (slot in recallSlots) {
        val text = json[slot]?.stringValue ?: continue
        result[slot] = JsonValue.String(substitute(definition, text) { resolve })
    }
    return result
}

internal fun FormEngine.itemAt(key: String?): RecallItem? =
    key?.let(core.visibility::parse)?.let {
        RecallItem(it.group, it.index)
    }

internal fun FormEngine.recalled(
    field: FormField,
    key: String? = null,
): FormField {
    val json = recalledSlots(field.json, itemAt(key))
    return if (json === field.json) field else field.withJson(json)
}

internal fun FormEngine.recalledTitle(json: JsonObject): String? = recalledSlots(json, null)["title"]?.stringValue

internal fun FormEngine.itemTemplate(
    template: String,
    item: RecallItem,
    params: JsonObject,
): String = substitute(definition, template, params) { recallScope().resolver(RecallOptions(item)) }
