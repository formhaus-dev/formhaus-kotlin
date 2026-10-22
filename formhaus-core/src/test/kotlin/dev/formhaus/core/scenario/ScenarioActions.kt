package dev.formhaus.core.scenario

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.SubmitHandler
import dev.formhaus.core.engine.SubmitResult
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.model.FieldOption

class ScenarioActions(
    private val engine: FormEngine,
    private val file: ScenarioFile,
    private val script: Script,
    private val log: EventLog,
) {
    private val actions: Map<String, suspend (JsonObject) -> JsonValue?> =
        mapOf(
            "start" to { _ -> engine.start().let { null } },
            "setValue" to { step -> engine.setValue(key(step), step["value"]).let { null } },
            "setItemValue" to { step ->
                engine.setItemValue(key(step), index(step), step["field"]?.stringValue.orEmpty(), step["value"]).let { null }
            },
            "addItem" to { step -> JsonValue.Bool(engine.addItem(key(step))) },
            "removeItem" to { step -> JsonValue.Bool(engine.removeItem(key(step), index(step))) },
            "setErrors" to { step -> engine.setErrors(fieldErrors(step["errors"]) ?: MutableStringMap()).let { null } },
            "setFieldLoading" to { step -> engine.setFieldLoading(key(step), step["loading"]?.boolValue ?: false).let { null } },
            "cancelPendingActions" to { _ -> engine.cancelPendingActions().let { null } },
            "next" to { _ -> JsonValue.Bool(engine.next()) },
            "back" to { _ -> JsonValue.Bool(engine.back()) },
            "skip" to { step -> JsonValue.Bool(engine.skip(step["handler"]?.takeIf { it.isTruthy }?.let(::handler))) },
            "submit" to { step -> JsonValue.Bool(engine.submit(handler(step["handler"]))) },
            "validate" to { _ -> json(engine.validate()) },
            "validateField" to { step -> engine.validateField(key(step))?.let(JsonValue::String) },
            "goToStepWithField" to { step -> engine.goToStepWithField(key(step)).let { null } },
            "goToStep" to { step -> json(engine.goToStep(step["stepId"]?.stringValue.orEmpty())) },
            "reset" to { step -> engine.reset(step["values"]?.objectValue).let { null } },
            "replaceDefinition" to { step -> engine.replaceDefinition(file.localized(step["definition"] ?: JsonValue.Null)).let { null } },
            "orderOptions" to { step ->
                val options = step["options"]?.arrayValue?.mapNotNull { option -> option.objectValue?.let(::FieldOption) }
                optionValues(engine.orderOptions(key(step), options))
            },
        )

    suspend fun act(step: JsonObject): JsonValue? {
        val name = step["do"]?.stringValue
        val action = actions[name] ?: throw ScriptError("unknown action $name")
        return action(step)
    }

    private fun key(step: JsonObject): String = step["key"]?.stringValue.orEmpty()

    private fun index(step: JsonObject): Int = step["index"]?.numberValue?.takeIf { it == Math.floor(it) }?.toInt() ?: -1

    private fun handler(entries: JsonValue?): SubmitHandler {
        val replies = Replies(entries?.arrayValue.orEmpty())
        return { values, skipped ->
            log.record(
                "call" to JsonValue.String("submit"),
                "values" to JsonValue.Object(values),
                "skippedSteps" to json(skipped),
            )
            SubmitResult.of(script.reply(replies.next()))
        }
    }
}

internal fun optionValues(options: List<FieldOption>): JsonValue = json(options.mapNotNull { it.value })
