package dev.formhaus.core.renderer

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.RecallItem
import dev.formhaus.core.engine.errorKeys
import dev.formhaus.core.engine.itemTemplate
import dev.formhaus.core.engine.recalled
import dev.formhaus.core.engine.recalledTitle
import dev.formhaus.core.engine.reportMissingLabel
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.StepButton
import dev.formhaus.core.validation.cut
import dev.formhaus.core.validation.noParams

private val FormEngine.definitionLabels: LabelSource? get() = definition.labels?.let(LabelSource::Catalog)

@RendererApi
public fun FormEngine.label(
    key: String,
    source: LabelSource?,
    params: JsonObject = noParams,
): String {
    if (options.debugChecks) reportMissingLabel(key, source, definition, options)
    return labelFor(key, params, source, definitionLabels)
}

@RendererApi
private fun FormEngine.label(
    key: String,
    source: LabelSource?,
    shown: Boolean,
): String = if (shown) label(key, source) else labelFor(key, noParams, source, definitionLabels)

@RendererApi
public fun FormEngine.itemTitle(
    group: FormField,
    index: Int,
    source: LabelSource?,
): String {
    val name = recalled(group).label
    val params =
        stringMapOf(
            "label" to (name?.let(JsonValue::String) ?: JsonValue.Null),
            "index" to JsonValue.Number(index + 1),
        )
    group.itemLabel?.let { return itemTemplate(it, RecallItem(group.key, index), params) }
    return cut(label("itemLabel", source, params))
}

private class ErrorSection(
    val id: String,
    val title: String?,
    val fields: List<FormNode>,
)

@RendererApi
public fun FormEngine.errorGroups(source: LabelSource?): List<ErrorGroup> {
    val errors = core.errors
    if (errors.isEmpty()) return emptyList()
    val values = core.values.json
    val visibility = core.visibility
    val sections =
        if (isMultiStep) {
            visibleSteps.map { ErrorSection(it.id, recalledTitle(it.json), it.fields) }
        } else {
            listOf(ErrorSection(definition.id, recalledTitle(definition.json), definition.fields.orEmpty()))
        }
    return sections.mapIndexedNotNull { index, section ->
        val entries =
            errorKeys(section.fields, errors, values).mapNotNull { key ->
                val item = visibility.activeItem(key, values)
                val raw = item?.field ?: visibility.activeField(key, values) ?: return@mapNotNull null
                val message = errors[key] ?: return@mapNotNull null
                val name = recalled(raw, if (item == null) null else key).label.orEmpty()
                val label = item?.let { "$name (${itemTitle(it.group, it.index, source)})" } ?: name
                ErrorEntry(key, label, message)
            }
        if (entries.isEmpty()) return@mapIndexedNotNull null
        val title =
            section.title?.takeIf { it.isNotEmpty() }
                ?: label("stepTitle", source, stringMapOf("index" to JsonValue.Number(index + 1)))
        ErrorGroup(section.id, title, entries)
    }
}

@RendererApi
public fun FormEngine.actionsState(source: LabelSource?): FormActionsState {
    val step = currentStep
    val last = isLastStep || !isMultiStep
    val next = step?.next ?: StepButton.Absent
    val primaryAction = if (last) definition.submit else next.action
    val showBack = isMultiStep && !isFirstStep && step?.back != StepButton.Hidden
    val showSkip = step?.canSkip == true && next != StepButton.Hidden
    return FormActionsState(
        submitAction = definition.submit,
        nextAction = next.action,
        backAction = step?.back?.action,
        skipAction = step?.skip?.action,
        cancelAction = definition.cancel,
        isFirstStep = isFirstStep,
        isLastStep = last,
        isMultiStep = isMultiStep,
        primaryLabel =
            if (last) {
                definition.submit.label ?: label("submit", source)
            } else {
                next.action?.label
                    ?: label("continue", source)
            },
        primaryDisabled = isActionDisabled(primaryAction),
        showPrimary = last || next != StepButton.Hidden,
        showBack = showBack,
        backLabel = step?.back?.action?.label ?: label("back", source, showBack),
        showSkip = showSkip,
        skipLabel = step?.skip?.action?.label ?: label("skip", source, showSkip),
    )
}
