package dev.formhaus.core.scenario

import dev.formhaus.core.definitionvalidation.IssueCode
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.engine.HiddenValues
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.validation.MessageSource
import dev.formhaus.core.validation.ValidatorFn
import dev.formhaus.research.Research

class ScenarioHooks(private val scenario: JsonObject, val script: Script, val log: EventLog) {
    val unknownNames: List<String> get() = scenario["hooks"]?.objectValue?.keys.orEmpty().filter { it !in names }

    private fun replies(name: String): Replies? = scenario["hooks"]?.get(name)?.arrayValue?.let(::Replies)

    private fun validators(): MutableStringMap<ValidatorFn> {
        val result = MutableStringMap<ValidatorFn>()
        for ((name, spec) in scenario["validators"]?.objectValue ?: stringMapOf()) {
            val reject = spec["reject"]?.arrayValue.orEmpty()
            val error = spec["error"]?.stringValue.orEmpty()
            result[name] = { value, _ -> if (reject.any { jsonEqual(value, it) }) error else null }
        }
        return result
    }

    fun options(): FormEngineOptions {
        val before = replies("onBeforeStepChange")
        val after = replies("onAfterStepChange")
        val validate = replies("onStepValidate")
        val beforeSubmit = replies("onBeforeSubmit")
        val afterSubmit = replies("onAfterSubmit")
        return FormEngineOptions(
            onBeforeStepChange = before?.let { replies ->
                { context ->
                    log.record("call" to JsonValue.String("onBeforeStepChange"), "context" to json(context))
                    script.reply(replies.next())?.boolValue
                }
            },
            onAfterStepChange = after?.let { replies ->
                { context ->
                    log.record("call" to JsonValue.String("onAfterStepChange"), "context" to json(context))
                    script.reply(replies.next())
                }
            },
            onStepValidate = validate?.let { replies ->
                { stepId, values ->
                    log.record("call" to JsonValue.String("onStepValidate"), "stepId" to JsonValue.String(stepId), "values" to JsonValue.Object(values))
                    fieldErrors(script.reply(replies.next()))
                }
            },
            onBeforeSubmit = beforeSubmit?.let { replies ->
                { values ->
                    log.record("call" to JsonValue.String("onBeforeSubmit"), "values" to JsonValue.Object(values))
                    script.reply(replies.next())?.boolValue
                }
            },
            onAfterSubmit = afterSubmit?.let { replies ->
                { values, skipped ->
                    log.record(
                        "call" to JsonValue.String("onAfterSubmit"), "values" to JsonValue.Object(values), "skippedSteps" to json(skipped),
                    )
                    script.reply(replies.next())
                }
            },
            onFormEvent = { event -> log.record("event" to json(event)) },
            onWarning = { issue ->
                if (issue.code != IssueCode.MISSING_MESSAGE_KEY && issue.code != IssueCode.MISSING_LABEL_KEY) {
                    log.record("warning" to obj("code" to JsonValue.String(issue.code.rawValue), "path" to JsonValue.String(issue.path)))
                }
            },
            validators = validators(),
            messages = scenario["messages"]?.let(MessageSource::Catalog),
            seed = scenario["seed"]?.stringValue,
            research = Research(),
            hiddenValues = HiddenValues.of(scenario["hiddenValues"]?.stringValue) ?: HiddenValues.CLEAR,
        )
    }

    companion object {
        val names: List<String> = listOf("onBeforeStepChange", "onAfterStepChange", "onBeforeSubmit", "onAfterSubmit", "onStepValidate")
    }
}
