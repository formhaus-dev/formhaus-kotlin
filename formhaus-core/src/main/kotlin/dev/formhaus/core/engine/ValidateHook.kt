package dev.formhaus.core.engine

import dev.formhaus.core.json.FieldErrors
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

private fun EngineCore.shownPaths(submit: Boolean): List<String> {
    val paths = ArrayList<String>()
    val state = values.json
    visitShown(validateNodes(submit), getSubmitValues(), visibility.conditionValues(state), visibility.limits(state)) { path, _, _ ->
        paths.add(path)
    }
    return paths
}

internal val EngineCore.validatesWithRules: Boolean get() = onValidate != null && validateWithRules

internal fun EngineCore.clearFormErrors() {
    topLevelErrors = serverFormErrors
}

internal suspend fun FormEngine.passesValidate(
    validate: ValidateFn,
    values: JsonObject,
    stale: () -> Boolean,
    submit: Boolean,
    ruleErrors: FieldErrors,
): Boolean {
    val core = core
    val fields = core.shownPaths(submit)
    val result = suspended { validate(values, ValidateContext(core.currentStep?.id, fields, submit)) }
    if (stale()) return false
    if (result == null || result.isEmpty()) return true
    val known = fields.toHashSet()
    val errors = MutableStringMap<String>()
    val general = ArrayList<String>()
    for ((key, message) in result) if (key in known) errors[key] = message else general.add(message)
    errors.merge(ruleErrors)
    core.applyValidationErrors(errors, formErrors = general, navigate = submit)
    return false
}
