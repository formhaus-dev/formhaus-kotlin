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
    restoring: FormSnapshot? = null,
) {
    private val restored = restoring?.matching(definition, options)
    public val seed: String =
        restored?.seed?.takeIf { it.isNotEmpty() } ?: options.seed?.takeIf { it.isNotEmpty() } ?: options.research?.seed() ?: ""
    private val listeners = Listeners()
    private val ownerThread = OwnerThread(options.checksOn)
    internal val core: EngineCore

    public constructor(
        definition: JsonValue,
        values: JsonObject = MutableJsonObject(),
        options: FormEngineOptions = FormEngineOptions(),
        restoring: FormSnapshot? = null,
    ) : this(FormDefinition(definition), values, options, restoring)

    init {
        reportWarnings(definition, options)
        core =
            EngineCore(
                definition,
                restored?.values ?: values,
                options.validators,
                options.messages,
                options.hiddenValues == HiddenValues.KEEP,
                missingMessageReporter(options),
            )
        restored?.let(core::restore)
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
        ownerThread.check("subscribe")
        val id = listeners.add(listener)
        return { listeners.remove(id) }
    }

    public fun setValue(
        key: String,
        value: JsonValue?,
    ) {
        ownerThread.check("setValue")
        core.setValue(key, value)
    }

    public fun items(key: String): List<GroupItem> = core.items(key)

    public fun addItem(key: String): Boolean = mutating("addItem") { core.addItem(key) }

    public fun removeItem(
        key: String,
        index: Int,
    ): Boolean = mutating("removeItem") { core.removeItem(key, index) }

    public fun setItemValue(
        key: String,
        index: Int,
        field: String,
        value: JsonValue?,
    ): Unit = mutating("setItemValue") { core.setItemValue(key, index, field, value) }

    public fun setErrors(errors: FieldErrors): Unit = mutating("setErrors") { core.applyValidationErrors(errors, server = true) }

    public fun setFieldLoading(
        key: String,
        loading: Boolean,
    ) {
        ownerThread.check("setFieldLoading")
        core.fieldLoading.assign(key, if (loading) true else null)
        core.notify()
    }

    public fun cancelPendingActions() {
        ownerThread.check("cancelPendingActions")
        core.validationEpoch += 1
        core.operationEpoch += 1
        core.stepValidating = false
        core.submitting = false
        core.notify()
    }

    public fun start(): Unit = mutating("start") { core.startEvents() }

    public fun isStepSkipped(stepId: String): Boolean = core.isStepSkipped(stepId)

    public fun validate(): FieldErrors = mutating("validate") { core.validate() }

    public fun validateField(key: String): String? = mutating("validateField") { core.validateOne(key) }

    public fun getSubmitValues(): JsonObject = core.getSubmitValues(payload = true)

    public fun isActionDisabled(action: FormAction?): Boolean =
        isActionDisabled(action, core.getSubmitValues(), core.visibility.limits(core.values.json))

    public fun goToStepWithField(key: String) {
        ownerThread.check("goToStepWithField")
        if (!core.isMultiStep) return
        val target = core.visibility.findStepIndex(key, core.values.json, core.currentStepIndex)
        if (target == core.currentStepIndex) {
            core.notify()
        } else if (target != null) {
            moveTo(target)
        }
    }

    public fun goToStep(stepId: String): GoToStepResult = mutating("goToStep") { redirectTo(stepId) }

    public suspend fun next(): Boolean = mutating("next") { advance(StepChangeReason.NEXT) }

    public suspend fun back(): Boolean = mutating("back") { changeStep(false, StepChangeReason.BACK) }

    public suspend fun skip(handler: SubmitHandler? = null): Boolean = mutating("skip") { reportErrors { skipStep(handler) } }

    public suspend fun submit(handler: SubmitHandler): Boolean = mutating("submit") { reportErrors { submitForm(handler) } }

    public fun reset(values: JsonObject? = null): Unit = mutating("reset") { core.reset(values) }

    public fun replaceDefinition(definition: JsonValue): Unit = replaceDefinition(FormDefinition(definition))

    public fun replaceDefinition(definition: FormDefinition) {
        ownerThread.check("replaceDefinition")
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

    internal inline fun <Result> mutating(
        action: String,
        body: () -> Result,
    ): Result {
        ownerThread.check(action)
        return body()
    }

    internal fun moveTo(index: Int) {
        core.validationEpoch += 1
        core.currentStepIndex = index
        core.notify(structureChanged = true)
    }
}
