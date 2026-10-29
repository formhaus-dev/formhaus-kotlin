package dev.formhaus.core.validation

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.StringMap
import dev.formhaus.core.json.jsString
import dev.formhaus.core.json.strictEquals
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FieldValidation
import dev.formhaus.core.model.FormField
import dev.formhaus.core.regex.CallBudget
import dev.formhaus.core.regex.JsRegex
import dev.formhaus.core.visibility.isBlank

public typealias ValidatorFn = (JsonValue, JsonObject) -> String?
public typealias Validators = StringMap<ValidatorFn>

public class ValidateOptions(
    public val validators: Validators = stringMapOf(),
    public val messages: MessageSource? = null,
)

private fun isEmptyValue(
    value: JsonValue?,
    field: FormField,
): Boolean = isBlank(value) || (value == JsonValue.Bool(false) && field.type in booleanTypes)

internal val defaultOptions: ValidateOptions = ValidateOptions()

private val defaultResolver: ResolveMessage = namedResolver(messageResolver(null))

private class Bound(
    rule: String,
) {
    val min = "min$rule"
    val max = "max$rule"
    val minMessage = "min${rule}Message"
    val maxMessage = "max${rule}Message"
}

private val valueBound = Bound("")
private val lengthBound = Bound("Length")

private fun boundError(
    size: Double,
    bound: Bound,
    rules: FieldValidation,
    message: (String, JsonObject) -> String,
): String? {
    val min = rules.json[bound.min]?.numberValue
    if (min != null && size < min) {
        return rules.message(bound.minMessage) ?: message("min", stringMapOf("min" to JsonValue.Number(min)))
    }
    val max = rules.json[bound.max]?.numberValue
    if (max != null && size > max) {
        return rules.message(bound.maxMessage) ?: message("max", stringMapOf("max" to JsonValue.Number(max)))
    }
    return null
}

private fun patternError(
    value: JsonValue,
    rules: FieldValidation,
    message: () -> String,
): String? {
    val pattern = rules.pattern?.let(JsRegex::compile) ?: return null
    return if (rejects(pattern, jsString(value))) rules.message("patternMessage") ?: message() else null
}

internal fun rejects(
    pattern: JsRegex,
    text: String,
): Boolean {
    if (text.length > MAX_STRING_LENGTH) return true
    val call = CallBudget.current.value
    return call?.admitsPatternTest() == false || pattern.test(text, call) != true
}

private fun sizeError(
    value: JsonValue,
    rules: FieldValidation,
    field: FormField,
    resolve: ResolveMessage,
): String? =
    when (value) {
        is JsonValue.Number -> {
            boundError(value.value, valueBound, rules) { name, params -> resolve(MessageKey(name), params, field) }
        }

        is JsonValue.String -> {
            boundError(
                value.value.length.toDouble(),
                lengthBound,
                rules,
            ) { name, params -> resolve(MessageKey("${name}Length"), params, field) }
        }

        is JsonValue.Array -> {
            val single = value.items.singleOrNull()
            val bounds =
                if (single != null && exclusiveValues(field).any { strictEquals(it, single) }) {
                    FieldValidation(rules.json.toMutableStringMap().also { it.remove("minLength") })
                } else {
                    rules
                }
            boundError(
                value.items.size.toDouble(),
                lengthBound,
                bounds,
            ) { name, params -> resolve(MessageKey("${name}Items"), params, field) }
        }

        else -> {
            null
        }
    }

private fun fileListRules(field: FormField): FieldValidation {
    val rules = field.validation?.json?.toMutableStringMap() ?: MutableJsonObject()
    rules.assign("maxLength", JsonValue.Number(field.fileListMax))
    return FieldValidation(rules)
}

public fun validateFieldValue(
    field: FormField,
    input: JsonValue?,
    allValues: JsonObject,
    options: ValidateOptions = defaultOptions,
): String? = CallBudget.run { fieldValueError(field, input, allValues, options.validators, options.messages) }

internal fun fieldValueError(
    field: FormField,
    input: JsonValue?,
    allValues: JsonObject,
    validators: Validators,
    messages: MessageSource?,
    required: Boolean = true,
): String? {
    val value = if (field.isFileList) JsonValue.Array(fileItems(input)) else trimValue(field, input)
    val rules = if (field.isFileList) fileListRules(field) else field.validation ?: FieldValidation(noParams)
    val resolve = if (messages == null) defaultResolver else namedResolver(messageResolver(messages))
    if (value == null || isEmptyValue(value, field)) return emptyValueError(field, rules, allValues, required, resolve)
    clientTypeError(field, value, resolve)?.takeIf { it.isNotEmpty() }?.let { return it }
    val issue =
        sizeError(value, rules, field, resolve)
            ?: patternError(value, rules) { resolve(MessageKey.PATTERN, noParams, field) }
            ?: rules.matchField?.let { if (strictEquals(value, allValues[it])) null else matchMessage(field, rules, resolve) }
            ?: rules.validator?.let { validators[it]?.invoke(value, allValues) }
    return issue?.takeIf { it.isNotEmpty() }
}

private fun matchMessage(
    field: FormField,
    rules: FieldValidation,
    resolve: ResolveMessage,
): String = rules.message("matchFieldMessage") ?: resolve(MessageKey.MATCH_FIELD, noParams, field)

private fun emptyValueError(
    field: FormField,
    rules: FieldValidation,
    allValues: JsonObject,
    required: Boolean,
    resolve: ResolveMessage,
): String? {
    val rule = rules.required
    if (required && rule != null && rule.isTruthy) return rule.stringValue ?: resolve(MessageKey.REQUIRED, noParams, field)
    val match = rules.matchField
    return if (match != null && !isBlank(allValues[match])) matchMessage(field, rules, resolve) else null
}
