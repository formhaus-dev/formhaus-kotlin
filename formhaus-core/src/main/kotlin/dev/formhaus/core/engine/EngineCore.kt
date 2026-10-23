package dev.formhaus.core.engine

import dev.formhaus.core.i18n.LocaleCatalog
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormStep
import dev.formhaus.core.validation.MessageSource
import dev.formhaus.core.validation.ResolveMessage
import dev.formhaus.core.validation.Validators

internal class SkippedKeys(
    val id: String,
    var keys: List<String>,
)

internal class EngineCore(
    definition: FormDefinition,
    initialValues: JsonObject = MutableJsonObject(),
    val validators: Validators = MutableStringMap(),
    private val messageSource: MessageSource? = null,
    keep: Boolean = false,
    private val missingMessage: MissingMessageFn? = null,
    private val locale: List<LocaleCatalog> = emptyList(),
) {
    var definition: FormDefinition = definition
        private set
    var messages: ResolveMessage = watchedResolver(messageSource, definition, missingMessage, locale)
        private set
    var visibility = VisibilityState(definition, keep)
        private set
    var errors = MutableStringMap<String>()
    var values: Values = startValues(visibility, Values(initialValues), errors)
        private set
    var valuesGeneration = 0
        private set
    var errorsGeneration = 0
        private set
    var serverErrorsGeneration = 0
        private set
    var definitionGeneration = 0
        private set
    var serverErrors = MutableStringMap<String>()
        private set
    var topLevelErrors: List<String> = emptyList()
    var serverFormErrors: List<String> = emptyList()
    var currentStepIndex = 0
    var fieldLoading = MutableStringMap<Boolean>()
    var stepValidating = false
    var submitting = false
    val skipped = ArrayList<SkippedKeys>()
    var validationEpoch = 0
    var operationEpoch = 0
    var restarts = 0
    var latestOperation = 0
    var sending: Int? = null
    var started = false
    var viewedStep: String? = null
    var shownFields: ShownFields? = null
    var version = 0
        private set
    var keptCache: KeptCache? = null
    var emitEvent: ((FormEvent) -> Unit)? = null
    var onChange: (() -> Unit)? = null

    fun load(
        next: FormDefinition,
        initialValues: Values,
    ) {
        definition = next
        definitionGeneration += 1
        messages = watchedResolver(messageSource, next, missingMessage, locale)
        visibility = VisibilityState(next, visibility.keep)
        replaceValues(startValues(visibility, initialValues, errors))
    }

    fun replaceValues(next: Values) {
        values = next
        valuesGeneration += 1
    }

    fun replaceErrors(next: MutableStringMap<String>) {
        errors = next
        errorsGeneration += 1
    }

    fun replaceServerErrors(next: MutableStringMap<String>) {
        serverErrors = next
        serverErrorsGeneration += 1
    }

    fun reconcileHidden(
        initial: Boolean = false,
        written: String? = null,
    ): Set<String> = visibility.reconcileHidden(values, errors, initial, written)

    val isMultiStep: Boolean get() = definition.isMultiStep
    val view: VisibilityView get() = visibility.state(values.json, currentStepIndex)
    val visibleSteps: List<FormStep> get() = view.visibleSteps
    val currentStep: FormStep? get() = view.currentStep
    val visibleFields: List<FormField> get() = view.visibleFields
    val isFirstStep: Boolean get() = currentStepIndex == 0
    val isLastStep: Boolean get() = currentStepIndex >= visibleSteps.size - 1
    val canGoNext: Boolean get() = visibility.canGoNext(values.json, currentStepIndex, validators)

    fun isStepSkipped(stepId: String): Boolean = skipped.any { it.id == stepId }

    fun markSkipped(
        stepId: String,
        keys: List<String>,
    ) {
        val entry = skipped.firstOrNull { it.id == stepId }
        if (entry != null) entry.keys = keys else skipped.add(SkippedKeys(stepId, keys))
    }

    fun unmarkSkipped(stepId: String) {
        skipped.removeAll { it.id == stepId }
    }

    fun notify(
        structureChanged: Boolean = false,
        valuesChanged: Boolean = false,
    ) {
        visibility.markChanged(structureChanged, valuesChanged)
        version += 1
        onChange?.invoke()
        trackChanges(valuesChanged)
    }

    fun emit(event: FormEvent) {
        emitEvent?.invoke(event)
    }

    fun baselineFields() {
        if (emitEvent == null) {
            shownFields = null
        } else if (started && shownFields == null) {
            shownFields = visibility.trackShown(values, null).state
        }
    }

    fun startEvents() {
        if (started) return
        emit(FormEvent.FormViewed)
        started = true
        viewedStep = null
        trackChanges(true)
    }

    fun trackChanges(fields: Boolean) {
        if (!started) return
        if (fields && emitEvent != null) trackFields()
        val step = currentStep ?: return
        if (viewedStep != step.id) {
            viewedStep = step.id
            emit(FormEvent.StepViewed(step.id, currentStepIndex))
        }
    }

    private fun trackFields() {
        val tracked = visibility.trackShown(values, shownFields)
        shownFields = tracked.state
        for (flip in tracked.flips) {
            emit(
                if (flip.shown) FormEvent.FieldShown(flip.key) else FormEvent.FieldHidden(flip.key),
            )
        }
    }

    private companion object {
        fun startValues(
            visibility: VisibilityState,
            initialValues: Values,
            errors: MutableStringMap<String>,
        ): Values {
            val values =
                createValues(visibility.allFields, initialValues) { key, json -> visibility.activeField(key, json) }
            visibility.reconcileHidden(values, errors, initial = true)
            return values
        }
    }
}
