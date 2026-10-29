package dev.formhaus.compose.model

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.formhaus.compose.fields.pruneUploads
import dev.formhaus.compose.fields.reconcileUploads
import dev.formhaus.compose.fields.releaseUploads
import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.engine.FormSnapshot
import dev.formhaus.core.engine.SubmitHandler
import dev.formhaus.core.i18n.Localized
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.renderer.ErrorGroup
import dev.formhaus.core.renderer.FormActionsState
import dev.formhaus.core.renderer.LabelSource
import dev.formhaus.core.renderer.actionsState
import dev.formhaus.core.renderer.firstErrorKey
import dev.formhaus.core.renderer.formatTemplate
import dev.formhaus.core.renderer.itemsHaveContent
import dev.formhaus.core.renderer.label
import dev.formhaus.core.renderer.nodeId
import dev.formhaus.core.renderer.onValidate
import dev.formhaus.core.renderer.restarts
import dev.formhaus.core.renderer.sending
import dev.formhaus.core.renderer.validateWithRules
import dev.formhaus.core.renderer.value
import dev.formhaus.core.renderer.version
import dev.formhaus.core.validation.MessageSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.atomic.AtomicLong

private val uploadOwners = AtomicLong()

@Stable
public class FormModel private constructor(
    public val engine: FormEngine,
    options: FormRendererOptions,
    onSubmit: SubmitHandler,
    private val hooks: HookBox?,
    onSnapshotChange: SnapshotChangeFn?,
) {
    public constructor(
        engine: FormEngine,
        options: FormRendererOptions = FormRendererOptions(),
        onSubmit: SubmitHandler,
    ) : this(engine, options, onSubmit, null, null)

    public constructor(
        definition: FormDefinition,
        initialValues: JsonObject = MutableJsonObject(),
        engineOptions: FormEngineOptions = FormEngineOptions(),
        options: FormRendererOptions = FormRendererOptions(),
        restoring: FormSnapshot? = null,
        onSnapshotChange: SnapshotChangeFn? = null,
        onSubmit: SubmitHandler,
    ) : this(EngineSetup(definition, initialValues, engineOptions, options, restoring = restoring), onSnapshotChange, onSubmit)

    public constructor(
        localized: Localized,
        initialValues: JsonObject = MutableJsonObject(),
        engineOptions: FormEngineOptions = FormEngineOptions(),
        options: FormRendererOptions = FormRendererOptions(),
        restoring: FormSnapshot? = null,
        onSnapshotChange: SnapshotChangeFn? = null,
        onSubmit: SubmitHandler,
    ) : this(
        EngineSetup(
            localized.definition,
            initialValues,
            engineOptions,
            options.copy(labels = options.labels ?: LabelSource.Catalog(localized.labels)),
            MessageSource.Catalog(localized.messages),
            restoring = restoring,
        ),
        onSnapshotChange,
        onSubmit,
    )

    internal constructor(setup: EngineSetup, onSnapshotChange: SnapshotChangeFn?, onSubmit: SubmitHandler) :
        this(setup.engine(), setup.renderer, onSubmit, setup.hooks, onSnapshotChange)

    public var options: FormRendererOptions = options
        private set
    internal var submitHandler: SubmitHandler = onSubmit
        private set
    internal val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val uploadOwner: Long = uploadOwners.incrementAndGet()
    internal val snapshots: SnapshotEmitter = SnapshotEmitter(engine, onSnapshotChange)

    public var structure: FormStructure by mutableStateOf(FormStructure(engine))
        private set
    public var stepValidating: Boolean by mutableStateOf(false)
        private set
    public var submitting: Boolean by mutableStateOf(false)
        private set
    internal val alerts: FormAlerts = FormAlerts(engine)
    public val topLevelErrors: List<String> get() = alerts.topLevelErrors
    public val errorGroups: List<ErrorGroup> get() = alerts.errorGroups
    public val stepErrorCounts: Map<String, Int> get() = alerts.stepErrorCounts
    public var actions: FormActionsState by mutableStateOf(engine.actionsState(options.labels))
        private set
    public val actionError: String? get() = alerts.actionError
    public var invalidAttempts: Int by mutableIntStateOf(0)
        internal set
    public var focusRequest: FocusRequest? by mutableStateOf(null)
        internal set
    public var version: Int by mutableIntStateOf(0)
        private set
    public var structureVersion: Int by mutableIntStateOf(0)
        private set
    public var valuesVersion: Int by mutableIntStateOf(0)
        private set
    private var loadingState by mutableStateOf(false)
    private var localeVersion by mutableIntStateOf(0)
    public var loading: Boolean
        get() = loadingState
        set(value) {
            if (loadingState == value) return
            loadingState = value
            loadingChanged()
        }
    public var disabled: Boolean by mutableStateOf(false)
        private set

    internal val fieldStates = mutableMapOf<String, FieldState>()
    private val groupStates = mutableMapOf<String, GroupState>()
    internal val optionStates = mutableMapOf<String, OptionsState>()
    internal var optionsRevision = -1
    internal val heldEdits = mutableMapOf<String, JsonValue?>()
    internal var revealPlan: RevealPlan? = null
    private var lastValues: JsonObject = engine.values
    internal val focusRequests = FocusRequests()
    private var emittedValues: JsonObject? = null
    private var restarts = engine.restarts
    private var closed = false
    private var unsubscribe: () -> Unit

    init {
        refreshStatus()
        unsubscribe = engine.subscribe(::refresh)
        reconcileUploads()
    }

    public val definition: FormDefinition get() = engine.definition

    public fun field(key: String): FieldState = fieldStates.getOrPut(key) { FieldState(key).also(::refreshField) }

    public fun group(key: String): GroupState = groupStates.getOrPut(key) { GroupState(key).also(::refreshGroup) }

    public fun label(
        key: String,
        params: JsonObject = MutableJsonObject(),
    ): String {
        val text = localeVersion.let { engine.label(key, options.labels, params) }
        val fallback = upcomingLabels[key]
        return if (text.isEmpty() && fallback != null) formatTemplate(fallback, params) else text
    }

    public fun start() {
        engine.start()
        reloadOptions()
        emitValues()
    }

    public fun close() {
        if (closed) return
        closed = true
        unsubscribe()
        snapshots.close()
        if (!engine.sending) {
            engine.cancelPendingActions()
            return finishClose()
        }
        unsubscribe =
            engine.subscribe {
                if (!engine.sending) {
                    unsubscribe()
                    finishClose()
                }
            }
    }

    private fun finishClose() {
        scope.cancel()
        releaseUploads()
    }

    internal fun updateHandlers(
        engineOptions: FormEngineOptions? = null,
        options: FormRendererOptions? = null,
        onSubmit: SubmitHandler? = null,
    ) {
        if (engineOptions != null && hooks != null) {
            hooks.current = engineOptions
            engine.onValidate = hooks.onValidate
            engine.validateWithRules = engineOptions.validateWithRules
        }
        if (engineOptions != null && engineOptions.locale != engine.locale) {
            localeVersion += 1
            alerts.invalidate()
            engine.locale = engineOptions.locale
        }
        if (options != null) this.options = options
        if (onSubmit != null) submitHandler = onSubmit
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
        if (valuesChanged || structureChanged) {
            reloadOptions()
            snapshots.changed()
        }
        if (valuesChanged) emitValues()
        if (engine.restarts != restarts) {
            restarts = engine.restarts
            pruneUploads(values)
        }
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
        alerts.refresh(options.labels, structureVersion, valuesVersion)
        actions = engine.actionsState(options.labels)
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
