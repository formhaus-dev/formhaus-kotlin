package dev.formhaus.compose.model

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.engine.SubmitHandler
import dev.formhaus.core.i18n.Localized
import dev.formhaus.core.json.FieldErrors
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.renderer.ErrorGroup
import dev.formhaus.core.renderer.FormActionsState
import dev.formhaus.core.renderer.LabelSource
import dev.formhaus.core.renderer.actionsState
import dev.formhaus.core.renderer.errorGroups
import dev.formhaus.core.renderer.errorsVersion
import dev.formhaus.core.renderer.firstErrorKey
import dev.formhaus.core.renderer.formatTemplate
import dev.formhaus.core.renderer.itemsHaveContent
import dev.formhaus.core.renderer.label
import dev.formhaus.core.renderer.nodeId
import dev.formhaus.core.renderer.value
import dev.formhaus.core.renderer.version
import dev.formhaus.core.validation.MessageSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@Stable
public class FormModel private constructor(
    public val engine: FormEngine,
    options: FormRendererOptions,
    onSubmit: SubmitHandler,
    private val hooks: HookBox?,
) {
    public constructor(
        engine: FormEngine,
        options: FormRendererOptions = FormRendererOptions(),
        onSubmit: SubmitHandler,
    ) : this(engine, options, onSubmit, null)

    public constructor(
        definition: FormDefinition,
        initialValues: JsonObject = MutableJsonObject(),
        engineOptions: FormEngineOptions = FormEngineOptions(),
        options: FormRendererOptions = FormRendererOptions(),
        onSubmit: SubmitHandler,
    ) : this(EngineSetup(definition, initialValues, engineOptions, options), onSubmit)

    public constructor(
        localized: Localized,
        initialValues: JsonObject = MutableJsonObject(),
        engineOptions: FormEngineOptions = FormEngineOptions(),
        options: FormRendererOptions = FormRendererOptions(),
        onSubmit: SubmitHandler,
    ) : this(
        EngineSetup(
            localized.definition,
            initialValues,
            engineOptions,
            options.copy(labels = options.labels ?: LabelSource.Catalog(localized.labels)),
            MessageSource.Catalog(localized.messages),
        ),
        onSubmit,
    )

    internal constructor(setup: EngineSetup, onSubmit: SubmitHandler) :
        this(setup.engine(), setup.renderer, onSubmit, setup.hooks)

    public var options: FormRendererOptions = options
        private set
    internal var submitHandler: SubmitHandler = onSubmit
        private set
    internal val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    public var structure: FormStructure by mutableStateOf(FormStructure(engine))
        private set
    public var stepValidating: Boolean by mutableStateOf(false)
        private set
    public var submitting: Boolean by mutableStateOf(false)
        private set
    public var topLevelErrors: List<String> by mutableStateOf(emptyList())
        private set
    public var errorGroups: List<ErrorGroup> by mutableStateOf(emptyList())
        private set
    public var stepErrorCounts: Map<String, Int> by mutableStateOf(emptyMap())
        private set
    public var actions: FormActionsState by mutableStateOf(engine.actionsState(options.labels))
        private set
    public var actionError: String? by mutableStateOf(null)
        private set
    public var invalidAttempts: Int by mutableIntStateOf(0)
        private set
    public var focusRequest: FocusRequest? by mutableStateOf(null)
        private set
    public var version: Int by mutableIntStateOf(0)
        private set
    public var structureVersion: Int by mutableIntStateOf(0)
        private set
    public var valuesVersion: Int by mutableIntStateOf(0)
        private set
    private var loadingState by mutableStateOf(false)
    public var loading: Boolean
        get() = loadingState
        set(value) {
            if (loadingState == value) return
            loadingState = value
            loadingChanged()
        }
    public var disabled: Boolean by mutableStateOf(false)
        private set
    internal var alertVersion: Int by mutableIntStateOf(0)
        private set
    internal var announcement: Announcement? by mutableStateOf(null)
        private set

    internal val fieldStates = mutableMapOf<String, FieldState>()
    private val groupStates = mutableMapOf<String, GroupState>()
    internal val optionStates = mutableMapOf<String, OptionsState>()
    internal var optionsRevision = -1
    internal val heldEdits = mutableMapOf<String, JsonValue?>()
    private var lastValues: JsonObject = engine.values
    private var lastErrors: FieldErrors = engine.errors
    private var seenErrors = -1
    private var lastStructureVersion = -1
    private var lastValuesVersion = -1
    private var focusCounter = 0
    private var pendingFocusKey: String? = null
    private var emittedValues: JsonObject? = null
    private val unsubscribe: () -> Unit

    init {
        refreshStatus()
        unsubscribe = engine.subscribe(::refresh)
    }

    public val definition: FormDefinition get() = engine.definition

    public fun field(key: String): FieldState = fieldStates.getOrPut(key) { FieldState(key).also(::refreshField) }

    public fun group(key: String): GroupState = groupStates.getOrPut(key) { GroupState(key).also(::refreshGroup) }

    public fun label(
        key: String,
        params: JsonObject = MutableJsonObject(),
    ): String {
        val text = engine.label(key, options.labels, params)
        val fallback = upcomingLabels[key]
        return if (text.isEmpty() && fallback != null) formatTemplate(fallback, params) else text
    }

    public fun start() {
        engine.start()
        reloadOptions()
        emitValues()
    }

    public fun close() {
        unsubscribe()
        scope.cancel()
    }

    internal fun updateHandlers(
        engineOptions: FormEngineOptions? = null,
        options: FormRendererOptions? = null,
        onSubmit: SubmitHandler? = null,
    ) {
        if (engineOptions != null) hooks?.current = engineOptions
        if (options != null) this.options = options
        if (onSubmit != null) submitHandler = onSubmit
    }

    internal fun errorText(key: String): String? =
        errorGroups.firstNotNullOfOrNull { group ->
            group.entries
                .firstOrNull {
                    it.key == key
                }?.text
        }

    internal fun requestFocus(
        key: String?,
        afterErrors: Boolean = false,
    ) {
        focusCounter += 1
        val summary = afterErrors && key != null && errorGroups.sumOf { it.entries.size } > 1
        focusRequest = FocusRequest(key, focusCounter, summary)
        pendingFocusKey?.takeIf { it != key }?.let { fieldStates[it]?.dropFocusRequest() }
        pendingFocusKey = key
        if (key != null) field(key).requestFocus(focusCounter)
    }

    internal fun dropFocusRequest() {
        if (focusRequest == null) return
        focusRequest = null
        pendingFocusKey?.let { fieldStates[it]?.dropFocusRequest() }
        pendingFocusKey = null
    }

    internal fun noteInvalidAttempt() {
        invalidAttempts += 1
        requestFocus(engine.firstErrorKey(), afterErrors = true)
    }

    internal fun setActionError(message: String?) {
        actionError = message
        if (message != null) alertVersion += 1
    }

    internal fun announce(text: String) {
        announcement = Announcement(text, (announcement?.id ?: 0) + 1)
    }

    private fun refresh() {
        version = engine.version
        val previousStep = structure.currentStep?.nodeId
        val nextStructure = FormStructure(engine)
        val structureChanged = nextStructure != structure
        if (structureChanged) {
            structure = nextStructure
            structureVersion += 1
        }
        val values = engine.values
        val valuesChanged = values != lastValues
        lastValues = values
        if (valuesChanged) valuesVersion += 1
        fieldStates.values.forEach(::refreshField)
        groupStates.values.forEach(::refreshGroup)
        refreshStatus()
        if (previousStep != structure.currentStep?.nodeId) requestFocus(engine.firstErrorKey(), afterErrors = true)
        if (valuesChanged || structureChanged) reloadOptions()
        if (valuesChanged) emitValues()
    }

    private fun refreshGroup(state: GroupState) {
        val items = engine.items(state.key)
        state.update(items, if (items.isEmpty()) emptyList() else engine.itemsHaveContent(state.key))
    }

    private fun refreshField(state: FieldState) {
        state.update(engine.value(state.key), engine.errors[state.key], engine.fieldLoading[state.key] == true)
    }

    private fun refreshStatus() {
        stepValidating = engine.stepValidating
        submitting = engine.submitting
        topLevelErrors = engine.topLevelErrors
        if (engine.errorsVersion != seenErrors) {
            seenErrors = engine.errorsVersion
            if (topLevelErrors.isNotEmpty()) alertVersion += 1
        }
        actions = engine.actionsState(options.labels)
        val errors = engine.errors
        val recallChanged = !errors.isEmpty() && valuesVersion != lastValuesVersion
        val errorsChanged = errors != lastErrors || errorGroups.isEmpty() != errors.isEmpty()
        if (errorsChanged || structureVersion != lastStructureVersion || recallChanged) {
            lastErrors = errors
            lastStructureVersion = structureVersion
            lastValuesVersion = valuesVersion
            errorGroups = engine.errorGroups(options.labels)
            val stepErrors = engine.stepErrors
            stepErrorCounts = stepErrors.keys.associateWith { stepErrors[it].orEmpty().size }
        }
        refreshDisabled()
    }

    internal fun refreshDisabled() {
        disabled = loading || engine.stepValidating || engine.submitting
    }

    private fun emitValues() {
        val onValuesChange = options.onValuesChange ?: return
        val values = engine.values
        if (values == emittedValues) return
        emittedValues = values
        onValuesChange(values)
    }
}

internal data class Announcement(
    val text: String,
    val id: Int,
)
