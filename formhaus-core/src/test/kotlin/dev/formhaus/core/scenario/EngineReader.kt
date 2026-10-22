package dev.formhaus.core.scenario

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject

class EngineReader(
    private val engine: FormEngine,
    private val log: EventLog,
) {
    private val plain: Map<String, () -> JsonValue> =
        mapOf(
            "values" to { JsonValue.Object(engine.values) },
            "submitValues" to { JsonValue.Object(engine.getSubmitValues()) },
            "errors" to { json(engine.errors) },
            "topLevelErrors" to { json(engine.topLevelErrors) },
            "stepErrors" to { objectOf(engine.stepErrors.map { (key, keys) -> key to json(keys) }) },
            "step" to { engine.currentStep?.let { JsonValue.String(it.id) } ?: JsonValue.Null },
            "stepIndex" to { JsonValue.Number(engine.currentStepIndex) },
            "steps" to { json(engine.visibleSteps.map { it.id }) },
            "fields" to { json(engine.visibleFields.map { it.key }) },
            "skipped" to {
                json(
                    engine.definition.steps
                        .orEmpty()
                        .map { it.id }
                        .filter(engine::isStepSkipped),
                )
            },
            "isFirstStep" to { JsonValue.Bool(engine.isFirstStep) },
            "isLastStep" to { JsonValue.Bool(engine.isLastStep) },
            "canGoNext" to { JsonValue.Bool(engine.canGoNext) },
            "progress" to {
                obj(
                    "current" to JsonValue.Number(engine.progress.current),
                    "total" to JsonValue.Number(engine.progress.total),
                )
            },
            "submitting" to { JsonValue.Bool(engine.submitting) },
            "stepValidating" to { JsonValue.Bool(engine.stepValidating) },
            "fieldLoading" to { objectOf(engine.fieldLoading.map { (key, flag) -> key to JsonValue.Bool(flag) }) },
            "log" to { JsonValue.Array(log.drain()) },
        )

    private val keyed: Map<String, (String) -> JsonValue> =
        mapOf(
            "items" to { key -> JsonValue.Number(engine.items(key).size) },
            "distinctItemIds" to { key ->
                JsonValue.Number(
                    engine
                        .items(key)
                        .map { it.id }
                        .toSet()
                        .size,
                )
            },
            "options" to { key -> optionValues(engine.orderOptions(key)) },
        )

    fun read(
        name: String,
        expected: JsonValue?,
    ): JsonValue? = plain[name]?.invoke() ?: keyed[name]?.let { read -> keysOf(expected).associateWith(read).toObject() }

    private fun keysOf(expected: JsonValue?): Set<String> =
        expected
            ?.objectValue
            ?.keys
            .orEmpty()
            .toSet()

    private fun Map<String, JsonValue>.toObject(): JsonValue = objectOf(entries.map { it.key to it.value })

    private fun objectOf(entries: List<Pair<String, JsonValue>>): JsonValue =
        JsonValue.Object(MutableJsonObject().also { map -> for ((key, value) in entries) map[key] = value })
}
