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

private class Pending(
    val label: String,
    val job: Job,
    val done: BooleanArray,
)

class ScenarioRun(
    private val file: ScenarioFile,
    private val scenario: JsonObject,
) {
    private val script = Script()
    private val log = EventLog()
    private val hooks = ScenarioHooks(scenario, script, log)
    private val failures = hooks.unknownNames.map { "unknown hook $it" }.toMutableList()
    private val pending = ArrayList<Pending>()
    private val launched = ArrayList<Job>()
    private val engine =
        FormEngine(file.runtime(scenario), scenario["values"]?.objectValue ?: MutableJsonObject(), hooks.options())
    private val actions = ScenarioActions(engine, file, script, log)
    private val reader = EngineReader(engine, log)

    private fun check(
        label: String,
        actual: JsonValue?,
        expected: JsonValue?,
    ) {
        if (jsonEqual(actual, expected)) return
        failures.add("$label\n  expected: ${render(expected)}\n  actual:   ${render(actual)}")
    }

    private fun outcome(
        label: String,
        step: JsonObject,
        result: Result<JsonValue?>,
    ) {
        val expected = step["throws"]
        val error = result.exceptionOrNull()
        when {
            error == null && expected != null -> {
                check("$label throws", null, expected)
            }

            error == null -> {
                if ("returns" in step) check("$label returns", result.getOrNull(), step["returns"])
            }

            expected != null -> {
                check(
                    "$label throws",
                    errorProjection(
                        error,
                        expected.objectValue ?: MutableJsonObject(),
                    ),
                    expected,
                )
            }

            else -> {
                failures.add("$label threw unexpectedly: $error")
            }
        }
    }

    private fun expect(
        index: Int,
        expectation: JsonObject,
    ) {
        val actual =
            expectation.map { (key, value) ->
                key to
                    reader.read(key, value).also { if (it == null) failures.add("unknown expectation $key") }
            }
        for ((key, value) in actual) check("step $index expect.$key", value, expectation[key])
        if ("log" !in expectation) log.entries.clear()
    }

    private fun start(
        scope: CoroutineScope,
        label: String,
        step: JsonObject,
    ): BooleanArray {
        val done = BooleanArray(1)
        val job =
            scope.launch {
                val result =
                    try {
                        Result.success(actions.act(step))
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

    suspend fun run(scope: CoroutineScope): List<String> =
        try {
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
                "settle" -> {
                    if (!settle(pending.toList())) return failures
                }

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
        if (script.pending.isNotEmpty()) {
            failures.add(
                "unresolved deferred reply(s): ${script.pending.joinToString(", ")}",
            )
        }
        return failures
    }
}
