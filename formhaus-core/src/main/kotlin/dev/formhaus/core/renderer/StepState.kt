package dev.formhaus.core.renderer

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.StepChangeReason
import dev.formhaus.core.engine.advance
import dev.formhaus.core.engine.errorKeys
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.FormStep

@RendererApi
public fun FormEngine.visibleNodes(step: FormStep): List<FormNode> = core.visibility.shownNodes(step.fields, core.values.json)

@RendererApi
public suspend fun FormEngine.autoAdvance(): Boolean = mutating("autoAdvance") { advance(StepChangeReason.AUTO_ADVANCE) }

@RendererApi
public fun FormEngine.firstErrorKey(): String? {
    val errors = core.errors
    if (errors.isEmpty()) return null
    return errorKeys(currentStep?.fields ?: definition.fields.orEmpty(), errors, core.values.json).firstOrNull()
}
