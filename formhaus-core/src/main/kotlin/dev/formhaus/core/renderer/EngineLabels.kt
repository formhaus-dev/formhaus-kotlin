package dev.formhaus.core.renderer

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.RecallItem
import dev.formhaus.core.engine.itemTemplate
import dev.formhaus.core.engine.recalled
import dev.formhaus.core.engine.reportMissingLabel
import dev.formhaus.core.i18n.labelText
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.StepButton
import dev.formhaus.core.validation.cut
import dev.formhaus.core.validation.noParams

private fun FormEngine.resolvedLabel(
    key: String,
    source: LabelSource?,
    params: JsonObject,
): String =
    labelFor(
        key,
        params,
        source,
        definition.labels?.let(LabelSource::Catalog),
        LabelSource.Function { name, values -> core.locale.labelText(name, values) },
    )

@RendererApi
public fun FormEngine.label(
    key: String,
    source: LabelSource?,
    params: JsonObject = noParams,
): String {
    if (options.checksOn) reportMissingLabel(key, source, definition, options, core.locale)
    return resolvedLabel(key, source, params)
}

@RendererApi
private fun FormEngine.label(
    key: String,
    source: LabelSource?,
    shown: Boolean,
): String = if (shown) label(key, source) else resolvedLabel(key, source, noParams)

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
