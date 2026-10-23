package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.validation.visitShown

private fun EngineCore.validateNodes(submit: Boolean): List<FormNode> =
    when {
        !submit -> currentStep?.fields.orEmpty()
        isMultiStep -> visibleSteps.filterNot { isStepSkipped(it.id) }.flatMap { it.fields }
        else -> definition.fields.orEmpty()
    }

internal fun EngineCore.clearFormErrors() {
    topLevelErrors = serverFormErrors
}

internal suspend fun FormEngine.passesValidate(
    validate: ValidateFn,
    values: JsonObject,
    stale: () -> Boolean,
    submit: Boolean,
): Boolean {
    val core = core
    val fields = ArrayList<String>()
    val state = core.values.json
    visitShown(core.validateNodes(submit), core.getSubmitValues(), core.visibility.conditionValues(state), core.visibility.limits(state)) {
        path,
        _,
        _,
        ->
        fields.add(path)
    }
    val result = suspended { validate(values, ValidateContext(core.currentStep?.id, fields, submit)) }
    if (stale()) return false
    if (result == null || result.isEmpty()) return true
    val known = fields.toHashSet()
    val errors = MutableStringMap<String>()
    val general = ArrayList<String>()
    for ((key, message) in result) if (key in known) errors[key] = message else general.add(message)
    core.applyValidationErrors(errors, formErrors = general, navigate = submit)
    return false
}
