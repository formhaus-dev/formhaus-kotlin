package dev.formhaus.core.engine

import dev.formhaus.core.json.FieldErrors
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.json.StringMap
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.model.FormAction
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.FormStep
import dev.formhaus.core.visibility.isActionDisabled

internal class Listeners {
    private var next = 0
    private val entries = ArrayList<Pair<Int, () -> Unit>>()

    fun add(listener: () -> Unit): Int {
        next += 1
        entries.add(next to listener)
        return next
    }

    fun remove(id: Int) {
        entries.removeAll { it.first == id }
    }

    fun fire() {
        var last = 0
        while (true) {
            val position = entries.binarySearchBy(last + 1) { it.first }.let { if (it < 0) -it - 1 else it }
            val entry = entries.getOrNull(position) ?: return
            last = entry.first
            entry.second()
        }
    }
}

public class FormEngine(
    definition: FormDefinition,
    values: JsonObject = MutableJsonObject(),
    internal val options: FormEngineOptions = FormEngineOptions(),
) {
    public val seed: String = options.seed?.takeIf { it.isNotEmpty() } ?: options.research?.seed() ?: ""
    private val listeners = Listeners()
    internal val core: EngineCore

    public constructor(definition: JsonValue, values: JsonObject = MutableJsonObject(), options: FormEngineOptions = FormEngineOptions()) :
        this(FormDefinition(definition), values, options)

    init {
        reportWarnings(definition, options)
        core =
            EngineCore(
                definition,
                values,
                options.validators,
                options.messages,
                options.hiddenValues == HiddenValues.KEEP,
                missingMessageReporter(options),
            )
        core.emitEvent = options.onFormEvent
        core.onChange = listeners::fire
    }

    public val definition: FormDefinition get() = core.definition
    public val values: JsonObject get() = if (core.visibility.keep) core.keptJson() else core.values.json
    public val errors: FieldErrors get() = core.errors.toMutableStringMap()
    public val currentStepIndex: Int get() = core.currentStepIndex
    public val fieldLoading: StringMap<Boolean> get() = core.fieldLoading.toMutableStringMap()
    public val stepValidating: Boolean get() = core.stepValidating
    public val submitting: Boolean get() = core.submitting
    public val isMultiStep: Boolean get() = core.isMultiStep
    public val visibleSteps: List<FormStep> get() = core.visibleSteps
    public val currentStep: FormStep? get() = core.currentStep
    public val visibleFields: List<FormField> get() = core.visibleFields
    public val visibleNodes: List<FormNode> get() = core.view.visibleNodes
    public val isFirstStep: Boolean get() = core.isFirstStep
    public val isLastStep: Boolean get() = core.isLastStep
    public val canGoNext: Boolean get() = core.canGoNext

    public val topLevelErrors: List<String>
        get() {
            val errors = core.errors
            if (errors.isEmpty()) return core.topLevelErrors
            val visibility = core.visibility
            val hidden =
                visibility.allFields.mapNotNull { field ->
                    val message =
                        errors[field.key]?.takeIf { field.type == "hidden" && it.isNotEmpty() }
                            ?: return@mapNotNull null
                    val active = visibility.activeField(field.key, core.values.json) ?: field
                    if (active.ordinal == field.ordinal) message else null
                }
            return core.topLevelErrors + hidden
        }

    public val stepErrors: StringMap<List<String>>
        get() {
            val result = MutableStringMap<List<String>>()
            for (step in visibleSteps) {
                val keys = errorKeys(step.fields, core.errors, core.values.json)
                if (keys.isNotEmpty()) result[step.id] = keys
            }
            return result
        }

    public val progress: StepProgress
        get() {
            val total = visibleSteps.size
            return StepProgress(minOf(currentStepIndex + 1, total), total)
        }

    public fun subscribe(listener: () -> Unit): () -> Unit {
        val id = listeners.add(listener)
        return { listeners.remove(id) }
    }

    public fun setValue(
        key: String,
        value: JsonValue?,
    ): Unit = core.setValue(key, value)

    public fun items(key: String): List<GroupItem> = core.items(key)

    public fun addItem(key: String): Boolean = core.addItem(key)

    public fun removeItem(
        key: String,
        index: Int,
    ): Boolean = core.removeItem(key, index)

    public fun setItemValue(
        key: String,
        index: Int,
        field: String,
        value: JsonValue?,
    ): Unit = core.setItemValue(key, index, field, value)

    public fun setErrors(errors: FieldErrors): Unit = core.applyValidationErrors(errors, server = true)

    public fun setFieldLoading(
        key: String,
        loading: Boolean,
    ) {
        core.fieldLoading.assign(key, if (loading) true else null)
        core.notify()
    }

    public fun cancelPendingActions() {
        core.validationEpoch += 1
        core.operationEpoch += 1
        core.stepValidating = false
        core.submitting = false
        core.notify()
    }

    public fun start(): Unit = core.startEvents()

    public fun isStepSkipped(stepId: String): Boolean = core.isStepSkipped(stepId)

    public fun validate(): FieldErrors = core.validate()

    public fun validateField(key: String): String? = core.validateOne(key)

    public fun getSubmitValues(): JsonObject = core.getSubmitValues(payload = true)

    public fun isActionDisabled(action: FormAction?): Boolean =
        isActionDisabled(action, core.getSubmitValues(), core.visibility.limits(core.values.json))

    public fun goToStepWithField(key: String) {
        if (!core.isMultiStep) return
        val target = core.visibility.findStepIndex(key, core.values.json, core.currentStepIndex)
        if (target == core.currentStepIndex) {
            core.notify()
        } else if (target != null) {
            moveTo(target)
        }
    }

    public fun goToStep(stepId: String): GoToStepResult = redirectTo(stepId)

    public suspend fun next(): Boolean = advance(StepChangeReason.NEXT)

    public suspend fun back(): Boolean = changeStep(false, StepChangeReason.BACK)

    public suspend fun skip(handler: SubmitHandler? = null): Boolean = reportErrors { skipStep(handler) }

    public suspend fun submit(handler: SubmitHandler): Boolean = reportErrors { submitForm(handler) }

    public fun reset(values: JsonObject? = null): Unit = core.reset(values)

    public fun replaceDefinition(definition: JsonValue): Unit = replaceDefinition(FormDefinition(definition))

    public fun replaceDefinition(definition: FormDefinition) {
        reportWarnings(definition, options)
        core.replaceDefinition(definition)
    }

    public fun orderOptions(
        key: String,
        options: List<FieldOption>? = null,
    ): List<FieldOption> = orderedOptions(key, options)

    public fun recallText(
        text: String,
        options: RecallOptions = RecallOptions(),
    ): String = substitute(definition, text) { recallScope().resolver(options) }

    internal fun moveTo(index: Int) {
        core.validationEpoch += 1
        core.currentStepIndex = index
        core.notify(structureChanged = true)
    }
}
