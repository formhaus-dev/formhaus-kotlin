package dev.formhaus.core.server

import dev.formhaus.core.engine.EngineCore
import dev.formhaus.core.engine.HiddenValues
import dev.formhaus.core.engine.VisibilityState
import dev.formhaus.core.engine.getSubmitValues
import dev.formhaus.core.engine.loadSubmitted
import dev.formhaus.core.engine.skipStep
import dev.formhaus.core.engine.validate
import dev.formhaus.core.json.FieldErrors
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.json.ValueParts
import dev.formhaus.core.json.describe
import dev.formhaus.core.json.equalParts
import dev.formhaus.core.json.partsHash
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.regex.CallBudget
import dev.formhaus.core.validation.MessageKey
import dev.formhaus.core.validation.MessageSource
import dev.formhaus.core.validation.ValidatorFn
import dev.formhaus.core.validation.Validators
import dev.formhaus.core.validation.messageResolver
import dev.formhaus.core.validation.namedResolver
import dev.formhaus.core.validation.noParams
import dev.formhaus.core.validation.typeError

public class SubmissionOptions(
    public val skippedSteps: JsonValue? = null,
    public val validators: Validators = stringMapOf(),
    public val messages: MessageSource? = null,
    public val hiddenValues: HiddenValues = HiddenValues.CLEAR,
) {
    public constructor(
        skippedSteps: List<String>,
        validators: Validators = stringMapOf(),
        messages: MessageSource? = null,
        hiddenValues: HiddenValues = HiddenValues.CLEAR,
    ) : this(JsonValue.Array(skippedSteps.map(JsonValue::String)), validators, messages, hiddenValues)
}

public class SubmissionResult internal constructor(
    public val values: JsonObject,
    public val errors: FieldErrors,
) {
    override fun equals(other: Any?): Boolean = equalParts(other, SubmissionResult::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("SubmissionResult")

    private fun parts(): ValueParts = listOf("values" to values, "errors" to errors)
}

private const val BODY_MESSAGE = "Pass the values object as the second argument, not the request body with \"values\" and \"skippedSteps\"."

private fun setError(
    visibility: VisibilityState,
    errors: MutableStringMap<String>,
    key: String,
    error: String,
) {
    errors[key] = error
    if (visibility.groups.none { it.key == key }) return
    for (path in errors.keys) if (path != key && visibility.owner(path) == key) errors.remove(path)
}

private fun ignoredValidators(
    fields: List<FormField>,
    given: Validators,
): Validators {
    val validators = MutableStringMap<ValidatorFn>()
    for (field in fields.flatMap { listOf(it) + it.items }) {
        val name = field.validation?.validator?.takeIf { it.isNotEmpty() } ?: continue
        validators[name] = { _, _ -> null }
    }
    return validators.merging(given)
}

private class Prepared(
    val engine: EngineCore,
    val skipped: List<String>,
    val check: () -> SubmissionResult,
)

private fun applySkips(
    engine: EngineCore,
    skippedSteps: JsonValue?,
) {
    val list = skippedSteps?.arrayValue ?: return
    val requested = list.mapNotNullTo(HashSet()) { it.stringValue }
    var index = 0
    while (index < engine.visibleSteps.size) {
        val step = engine.visibleSteps[index]
        if (step.canSkip && step.id in requested) engine.skipStep(index)
        index += 1
    }
}

private fun prepare(
    definition: FormDefinition,
    fields: List<FormField>,
    values: JsonObject,
    options: SubmissionOptions,
): Prepared {
    val messages = messageResolver(options.messages, MessageSource.Catalog(definition.messages ?: JsonValue.Null))
    val resolve = namedResolver(messages)
    val resolved = activeValues(VisibilityState(definition), fields, values, resolve)
    val items = blankItems(resolved, fields, resolve)
    val validators = ignoredValidators(fields, options.validators)
    val engine = EngineCore(definition, resolved.values, validators, MessageSource.Function(messages))
    engine.loadSubmitted(resolved.values)
    applySkips(engine, options.skippedSteps)
    val skipped = engine.skipped.flatMap { it.keys }
    val check = {
        val errors = engine.validate().toMutableStringMap()
        val submitted = engine.getSubmitValues().toMutableStringMap()
        val visibility = engine.visibility
        for (invalid in resolved.invalid) {
            val field = visibility.activeField(invalid.key, engine.values.json) ?: continue
            if (invalid.key in skipped) continue
            setError(
                visibility,
                errors,
                invalid.key,
                typeError(field, invalid.value, resolve) ?: resolve(MessageKey.VALUE, noParams, field),
            )
            submitted.remove(invalid.key)
        }
        for (item in items) {
            if (visibility.owner(item.key) in submitted &&
                visibility.activeItem(item.key, engine.values.json) != null
            ) {
                errors[item.key] = item.error
            }
        }
        if (options.hiddenValues == HiddenValues.KEEP) keepHidden(fields, resolved, submitted, skipped, validators)
        SubmissionResult(submitted, errors)
    }
    return Prepared(engine, skipped, check)
}

private fun withSkipped(
    source: JsonObject,
    prepared: Prepared,
): JsonObject {
    val values = source.toMutableStringMap()
    for (key in prepared.skipped) {
        values.remove(key)
        prepared.engine.values[key]?.let { values[key] = it }
    }
    return values
}

public fun validateSubmission(
    definition: JsonValue,
    values: JsonValue,
    options: SubmissionOptions = SubmissionOptions(),
): SubmissionResult = validateSubmission(FormDefinition(definition), values, options)

public fun validateSubmission(
    definition: FormDefinition,
    values: JsonValue,
    options: SubmissionOptions = SubmissionOptions(),
): SubmissionResult {
    val quadratic = refuseSlowPatterns(definition)
    return CallBudget.run { QuadraticPatterns.current.withValue(quadratic) { submissionResult(definition, values, options) } }
}

private fun submissionResult(
    definition: FormDefinition,
    values: JsonValue,
    options: SubmissionOptions,
): SubmissionResult {
    val fields = definition.allFields
    val source = values.objectValue ?: MutableJsonObject()
    var prepared = prepare(definition, fields, source, options)
    var round = 0
    while (round < definition.steps.orEmpty().size && prepared.skipped.isNotEmpty()) {
        val next = prepare(definition, fields, withSkipped(source, prepared), options)
        val settled = next.skipped.joinToString(",") == prepared.skipped.joinToString(",")
        prepared = next
        if (settled) break
        round += 1
    }
    val result = prepared.check()
    val errors = result.errors.toMutableStringMap()
    val isBody =
        values.objectValue?.contains("values") == true && fields.none { it.key == "values" || it.key == "skippedSteps" }
    if (isBody) errors["values"] = BODY_MESSAGE
    return SubmissionResult(result.values, errors)
}
