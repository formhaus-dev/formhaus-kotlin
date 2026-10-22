package dev.formhaus.compose.model

import android.content.Context
import dev.formhaus.compose.fields.withoutDeadUploads
import dev.formhaus.compose.platform.isDebuggable
import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.engine.FormSnapshot
import dev.formhaus.core.engine.SubmitHandler
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.validation.MessageSource

internal class EngineSetup(
    private val definition: FormDefinition,
    private val values: JsonObject,
    engineOptions: FormEngineOptions,
    val renderer: FormRendererOptions,
    private val messages: MessageSource? = null,
    private val debuggable: Boolean = false,
    val restoring: FormSnapshot? = null,
) {
    val hooks: HookBox = HookBox(engineOptions)

    fun engine(): FormEngine =
        FormEngine(definition, values, hooks.forwarding(renderer, messages, debuggable), restoring?.withoutDeadUploads())
}

internal fun ownedFormModel(
    context: Context,
    definition: FormDefinition,
    values: JsonObject,
    engineOptions: FormEngineOptions,
    renderer: FormRendererOptions,
    restoring: FormSnapshot?,
    onSnapshotChange: SnapshotChangeFn?,
    onSubmit: SubmitHandler,
): FormModel =
    FormModel(
        EngineSetup(definition, values, engineOptions, renderer, debuggable = context.isDebuggable, restoring = restoring),
        onSnapshotChange,
        onSubmit,
    )
