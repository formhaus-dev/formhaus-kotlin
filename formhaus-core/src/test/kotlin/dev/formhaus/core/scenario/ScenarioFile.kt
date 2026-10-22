package dev.formhaus.core.scenario

import dev.formhaus.core.definitionvalidation.FormDefinitionError
import dev.formhaus.core.engine.FormEvent
import dev.formhaus.core.engine.FormLifecycleError
import dev.formhaus.core.engine.GoToStepResult
import dev.formhaus.core.engine.StepChangeContext
import dev.formhaus.core.engine.SubmitFailureReason
import dev.formhaus.core.i18n.localize
import dev.formhaus.core.json.FieldErrors
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.json.jsString
import dev.formhaus.core.json.toJsonString
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.support.Fixtures

class ScenarioFile(
    val name: String,
    val definition: JsonValue,
    val overlay: JsonValue?,
    val scenarios: List<JsonObject>,
) {
    fun runtime(scenario: JsonObject): FormDefinition = localized(scenario["definition"] ?: definition)

    fun localized(source: JsonValue): FormDefinition = overlay?.let { localize(source, it).definition } ?: FormDefinition(source)

    companion object {
        val all: List<ScenarioFile> by lazy {
            Fixtures.scenarioNames.map { name ->
                val json = Fixtures.scenarioFile(name)
                ScenarioFile(
                    name,
                    json["definition"] ?: JsonValue.Null,
                    json["overlay"],
                    json["scenarios"]
                        ?.arrayValue
                        ?.mapNotNull {
                            it.objectValue
                        }.orEmpty(),
                )
            }
        }
    }
}

fun obj(vararg pairs: Pair<String, JsonValue>): JsonValue =
    JsonValue.Object(
        MutableJsonObject().also { map ->
            for ((key, value) in pairs) {
                map[key] =
                    value
            }
        },
    )

fun canonical(value: JsonValue?): JsonValue =
    when (value) {
        null, JsonValue.Null -> {
            JsonValue.Null
        }

        is JsonValue.Number -> {
            if (value.value.isFinite()) {
                JsonValue.Number(
                    if (value.value ==
                        0.0
                    ) {
                        0.0
                    } else {
                        value.value
                    },
                )
            } else {
                JsonValue.Null
            }
        }

        is JsonValue.Array -> {
            JsonValue.Array(value.items.map(::canonical))
        }

        is JsonValue.Object -> {
            JsonValue.Object(
                MutableJsonObject().also { result ->
                    for ((key, item) in value.members) {
                        result[key] =
                            canonical(item)
                    }
                },
            )
        }

        else -> {
            value
        }
    }

fun jsonEqual(
    lhs: JsonValue?,
    rhs: JsonValue?,
): Boolean = canonical(lhs) == canonical(rhs)

fun render(value: JsonValue?): String = (value ?: JsonValue.Null).toJsonString()

fun json(strings: List<String>): JsonValue = JsonValue.Array(strings.map(JsonValue::String))

fun json(errors: FieldErrors): JsonValue =
    JsonValue.Object(
        MutableJsonObject().also { map ->
            for ((key, value) in errors) {
                map[key] =
                    JsonValue.String(value)
            }
        },
    )

fun json(event: FormEvent): JsonValue {
    val fields = mutableListOf<Pair<String, JsonValue>>("type" to JsonValue.String(event.type))
    when (event) {
        is FormEvent.StepViewed -> {
            fields +=
                listOf("stepId" to JsonValue.String(event.stepId), "stepIndex" to JsonValue.Number(event.stepIndex))
        }

        is FormEvent.FieldError -> {
            fields +=
                listOf("fieldKey" to JsonValue.String(event.fieldKey), "error" to JsonValue.String(event.error))
        }

        is FormEvent.FieldShown -> {
            fields += "fieldKey" to JsonValue.String(event.fieldKey)
        }

        is FormEvent.FieldHidden -> {
            fields += "fieldKey" to JsonValue.String(event.fieldKey)
        }

        is FormEvent.StepCompleted -> {
            fields += "stepId" to JsonValue.String(event.stepId)
        }

        is FormEvent.StepSkipped -> {
            fields += "stepId" to JsonValue.String(event.stepId)
        }

        is FormEvent.FormSubmitted -> {
            fields += "fieldCount" to JsonValue.Number(event.fieldCount)
        }

        is FormEvent.FormSubmitFailed -> {
            fields += "reason" to JsonValue.String(event.reason.name)
            val status = (event.reason as? SubmitFailureReason.Error)?.status
            if (status != null) fields += "status" to JsonValue.Number(status)
        }

        is FormEvent.StepRedirected -> {
            fields +=
                listOf(
                    "requestedStepId" to JsonValue.String(event.requestedStepId),
                    "stepId" to (event.stepId?.let(JsonValue::String) ?: JsonValue.Null),
                    "reason" to JsonValue.String(event.reason.rawValue),
                )
        }

        FormEvent.FormViewed, FormEvent.FormSubmitSucceeded, FormEvent.FormReset -> {}
    }
    return obj(*fields.toTypedArray())
}

fun json(result: GoToStepResult): JsonValue {
    val fields =
        mutableListOf(
            "stepId" to (result.stepId?.let(JsonValue::String) ?: JsonValue.Null),
            "reached" to JsonValue.Bool(result.reached),
        )
    result.reason?.let { fields += "reason" to JsonValue.String(it.rawValue) }
    return obj(*fields.toTypedArray())
}

fun json(context: StepChangeContext): JsonValue =
    obj(
        "fromStepId" to JsonValue.String(context.fromStepId),
        "toStepId" to JsonValue.String(context.toStepId),
        "reason" to JsonValue.String(context.reason.rawValue),
        "values" to JsonValue.Object(context.values),
    )

fun errorProjection(
    error: Throwable,
    expected: JsonObject,
): JsonValue {
    val fields = mutableListOf<Pair<String, JsonValue>>()
    val message =
        when (error) {
            is ScriptError -> {
                fields += "name" to JsonValue.String("Error")
                error.message.orEmpty()
            }

            is FormLifecycleError -> {
                fields += "name" to JsonValue.String("FormLifecycleError")
                fields += "phase" to JsonValue.String(error.phase.rawValue)
                error.message.orEmpty()
            }

            is FormDefinitionError -> {
                fields += "name" to JsonValue.String("FormDefinitionError")
                error.message.orEmpty()
            }

            else -> {
                fields += "name" to JsonValue.String(error::class.simpleName.orEmpty())
                error.toString()
            }
        }
    if ("message" in expected) fields += "message" to JsonValue.String(message)
    return obj(*fields.toTypedArray())
}

fun fieldErrors(value: JsonValue?): FieldErrors? {
    val errors = value?.objectValue ?: return null
    return MutableStringMap<String>().also { result -> for ((key, message) in errors) result[key] = jsString(message) }
}
