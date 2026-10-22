package dev.formhaus.core.renderer

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.RecallItem
import dev.formhaus.core.engine.itemAt
import dev.formhaus.core.engine.recallKeys
import dev.formhaus.core.engine.recallSlots
import dev.formhaus.core.engine.recalled
import dev.formhaus.core.engine.recalledSlots
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormSection
import dev.formhaus.core.model.FormStep
import dev.formhaus.core.validation.hasRecallBraces

@RendererApi
public fun FormEngine.recalledTexts(field: FormField, at: String? = null): RecalledTexts = RecalledTexts(recalled(field, at).json)

@RendererApi
public fun FormEngine.recalledTexts(section: FormSection): RecalledTexts = RecalledTexts(recalledSlots(section.json, null))

@RendererApi
public fun FormEngine.recalledTexts(step: FormStep): RecalledTexts = RecalledTexts(recalledSlots(step.json, null))

private fun FormEngine.dependencies(texts: List<String?>, item: RecallItem?): List<RecallDependency> {
    if (!definition.recallsAnswers) return emptyList()
    val keys = texts.filterNotNull().filter(::hasRecallBraces).flatMap(::recallKeys)
    if (keys.isEmpty()) return emptyList()
    val groups = core.visibility.groups
    val scope = item?.let { owner -> groups.firstOrNull { it.key == owner.group } }?.items.orEmpty()
    return keys.map { key ->
        if (item != null && scope.any { it.key == key }) {
            RecallDependency("${item.group}.${item.index}.$key", false)
        } else {
            RecallDependency(key, groups.any { it.key == key })
        }
    }.distinct()
}

private fun slotTexts(json: JsonObject, skipped: String? = null): List<String?> =
    recallSlots.filter { it != skipped }.map { json[it]?.stringValue }

@RendererApi
public fun FormEngine.recallDependencies(field: FormField, at: String? = null): List<RecallDependency> =
    dependencies(slotTexts(field.json, "itemLabel"), itemAt(at))

@RendererApi
public fun FormEngine.recallDependencies(section: FormSection): List<RecallDependency> = dependencies(slotTexts(section.json), null)

@RendererApi
public fun FormEngine.recallDependencies(step: FormStep): List<RecallDependency> = dependencies(slotTexts(step.json), null)

@RendererApi
public fun FormEngine.itemTitleDependencies(group: FormField, index: Int): List<RecallDependency> =
    dependencies(listOf(group.label), null) + dependencies(listOf(group.itemLabel), RecallItem(group.key, index))
