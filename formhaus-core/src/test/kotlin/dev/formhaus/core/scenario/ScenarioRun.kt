package dev.formhaus.core.scenario

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.SubmitHandler
import dev.formhaus.core.engine.SubmitResult
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.model.FieldOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private class Pending(val label: String, val job: Job, val done: BooleanArray)

class ScenarioRun(private val file: ScenarioFile, private val scenario: JsonObject) {
    private val script = Script()
    private val log = EventLog()
    private val hooks = ScenarioHooks(scenario, script, log)
    private val failures = hooks.unknownNames.map { "unknown hook $it" }.toMutableList()
    private val pending = ArrayList<Pending>()
    private val launched = ArrayList<Job>()
    private val engine = FormEngine(file.runtime(scenario), scenario["values"]?.objectValue ?: MutableJsonObject(), hooks.options())

    private fun handler(entries: JsonValue?): SubmitHandler {
        val replies = Replies(entries?.arrayValue.orEmpty())
        return { values, skipped ->
            log.record("call" to JsonValue.String("submit"), "values" to JsonValue.Object(values), "skippedSteps" to json(skipped))
            SubmitResult.of(script.reply(replies.next()))
        }
    }

    private fun check(label: String, actual: JsonValue?, expected: JsonValue?) {
        if (jsonEqual(actual, expected)) return
        failures.add("$label\n  expected: ${render(expected)}\n  actual:   ${render(actual)}")
    }

    private fun outcome(label: String, step: JsonObject, result: Result<JsonValue?>) {
        val expected = step["throws"]
        val error = result.exceptionOrNull()
        when {
            error == null && expected != null -> check("$label throws", null, expected)
            error == null -> if ("returns" in step) check("$label returns", result.getOrNull(), step["returns"])
            expected != null -> check("$label throws", errorProjection(error, expected.objectValue ?: MutableJsonObject()), expected)
            else -> failures.add("$label threw unexpectedly: $error")
        }
    }

    private fun index(step: JsonObject): Int = step["index"]?.numberValue?.takeIf { it == Math.floor(it) }?.toInt() ?: -1

    private suspend fun act(step: JsonObject): JsonValue? {
        val key = step["key"]?.stringValue.orEmpty()
        when (val name = step["do"]?.stringValue) {
            "start" -> engine.start()
            "setValue" -> engine.setValue(key, step["value"])
            "setItemValue" -> engine.setItemValue(key, index(step), step["field"]?.stringValue.orEmpty(), step["value"])
            "addItem" -> return JsonValue.Bool(engine.addItem(key))
            "removeItem" -> return JsonValue.Bool(engine.removeItem(key, index(step)))
            "setErrors" -> engine.setErrors(fieldErrors(step["errors"]) ?: MutableStringMap())
            "setFieldLoading" -> engine.setFieldLoading(key, step["loading"]?.boolValue ?: false)
            "cancelPendingActions" -> engine.cancelPendingActions()
            "next" -> return JsonValue.Bool(engine.next())
            "back" -> return JsonValue.Bool(engine.back())
            "skip" -> return JsonValue.Bool(engine.skip(if (step["handler"]?.isTruthy == true) handler(step["handler"]) else null))
            "submit" -> return JsonValue.Bool(engine.submit(handler(step["handler"])))
            "validate" -> return json(engine.validate())
            "validateField" -> return engine.validateField(key)?.let(JsonValue::String)
            "goToStepWithField" -> engine.goToStepWithField(key)
            "goToStep" -> return json(engine.goToStep(step["stepId"]?.stringValue.orEmpty()))
            "reset" -> engine.reset(step["values"]?.objectValue)
            "replaceDefinition" -> engine.replaceDefinition(file.localized(step["definition"] ?: JsonValue.Null))
            "orderOptions" -> {
                val options = step["options"]?.arrayValue?.mapNotNull { option -> option.objectValue?.let(::optionOf) }
                return optionValues(engine.orderOptions(key, options))
            }
            else -> throw ScriptError("unknown action $name")
        }
        return null
    }

    private fun counted(expected: JsonValue?, count: (String) -> Int): JsonValue =
        JsonValue.Object(MutableJsonObject().also { map -> for (key in expected?.objectValue?.keys.orEmpty()) map[key] = JsonValue.Number(count(key)) })

    private fun read(name: String, expected: JsonValue?): JsonValue? = when (name) {
        "values" -> JsonValue.Object(engine.values)
        "submitValues" -> JsonValue.Object(engine.getSubmitValues())
        "errors" -> json(engine.errors)
        "topLevelErrors" -> json(engine.topLevelErrors)
        "stepErrors" -> JsonValue.Object(MutableJsonObject().also { map -> for ((key, keys) in engine.stepErrors) map[key] = json(keys) })
        "step" -> engine.currentStep?.let { JsonValue.String(it.id) } ?: JsonValue.Null
        "stepIndex" -> JsonValue.Number(engine.currentStepIndex)
        "steps" -> json(engine.visibleSteps.map { it.id })
        "fields" -> json(engine.visibleFields.map { it.key })
        "skipped" -> json(engine.definition.steps.orEmpty().map { it.id }.filter(engine::isStepSkipped))
        "items" -> counted(expected) { engine.items(it).size }
        "distinctItemIds" -> counted(expected) { key -> engine.items(key).map { it.id }.toSet().size }
        "isFirstStep" -> JsonValue.Bool(engine.isFirstStep)
        "isLastStep" -> JsonValue.Bool(engine.isLastStep)
        "canGoNext" -> JsonValue.Bool(engine.canGoNext)
        "progress" -> obj("current" to JsonValue.Number(engine.progress.current), "total" to JsonValue.Number(engine.progress.total))
        "submitting" -> JsonValue.Bool(engine.submitting)
        "stepValidating" -> JsonValue.Bool(engine.stepValidating)
        "fieldLoading" -> JsonValue.Object(MutableJsonObject().also { map -> for ((key, flag) in engine.fieldLoading) map[key] = JsonValue.Bool(flag) })
        "options" -> JsonValue.Object(
            MutableJsonObject().also { map -> for (key in expected?.objectValue?.keys.orEmpty()) map[key] = optionValues(engine.orderOptions(key)) },
        )
        "log" -> JsonValue.Array(log.drain())
        else -> {
            failures.add("unknown expectation $name")
            null
        }
    }

    private fun expect(index: Int, expectation: JsonObject) {
        val actual = expectation.map { (key, value) -> key to read(key, value) }
        for ((key, value) in actual) check("step $index expect.$key", value, expectation[key])
        if ("log" !in expectation) log.entries.clear()
    }

    private fun start(scope: CoroutineScope, label: String, step: JsonObject): BooleanArray {
        val done = BooleanArray(1)
        val job = scope.launch {
            val result = try {
                Result.success(act(step))
            } catch (error: Throwable) {
                Result.failure(error)
            }
            outcome(label, step, result)
            done[0] = true
        }
        pending.add(Pending(label, job, done))
        launched.add(job)
        return done
    }

    private suspend fun settle(entries: List<Pending>): Boolean {
        drain()
        pending.removeAll(entries)
        for (entry in entries) {
            if (!entry.done[0]) {
                failures.add("${entry.label} never settled")
                return false
            }
            entry.job.join()
        }
        return true
    }

    suspend fun run(scope: CoroutineScope): List<String> = try {
        runSteps(scope)
    } finally {
        for (job in launched) job.cancel()
    }

    private suspend fun runSteps(scope: CoroutineScope): List<String> {
        scenario["steps"]?.arrayValue.orEmpty().forEachIndexed { index, value ->
            val step = value.objectValue ?: MutableJsonObject()
            val expectation = step["expect"]?.objectValue
            if (expectation != null) {
                expect(index, expectation)
                return@forEachIndexed
            }
            val label = "step $index ${step["do"]?.stringValue.orEmpty()}"
            when (step["do"]?.stringValue) {
                "settle" -> if (!settle(pending.toList())) return failures
                "resolve" -> {
                    script.resolve(step["deferred"]?.stringValue.orEmpty())
                    drain()
                }
                else -> {
                    val done = start(scope, label, step)
                    if (step["await"] == JsonValue.Bool(false)) {
                        drain()
                    } else if (!settle(pending.filter { it.done === done })) {
                        return failures
                    }
                }
            }
        }
        val unsettled = pending.filter { !it.done[0] }.map { it.label }
        if (unsettled.isNotEmpty()) failures.add("unsettled action(s): ${unsettled.joinToString(", ")}")
        if (script.pending.isNotEmpty()) failures.add("unresolved deferred reply(s): ${script.pending.joinToString(", ")}")
        return failures
    }
}

private fun optionOf(json: JsonObject): FieldOption = FieldOption(json)

private fun optionValues(options: List<FieldOption>): JsonValue = json(options.mapNotNull { it.value })
