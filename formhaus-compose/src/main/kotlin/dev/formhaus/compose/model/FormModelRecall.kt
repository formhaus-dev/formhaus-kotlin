package dev.formhaus.compose.model

import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormSection
import dev.formhaus.core.model.FormStep
import dev.formhaus.core.renderer.RecallDependency
import dev.formhaus.core.renderer.RecalledTexts
import dev.formhaus.core.renderer.recallDependencies
import dev.formhaus.core.renderer.recalledTexts

internal fun FormModel.track(dependencies: List<RecallDependency>) {
    if (dependencies.isEmpty()) return
    structureVersion
    for (dependency in dependencies) {
        if (dependency.isGroup) group(dependency.key).items else field(dependency.key).value
    }
}

public fun FormModel.texts(
    field: FormField,
    key: String? = null,
): RecalledTexts {
    track(engine.recallDependencies(field, key))
    return engine.recalledTexts(field, key)
}

public fun FormModel.texts(section: FormSection): RecalledTexts {
    track(engine.recallDependencies(section))
    return engine.recalledTexts(section)
}

public fun FormModel.texts(step: FormStep): RecalledTexts {
    track(engine.recallDependencies(step))
    return engine.recalledTexts(step)
}
