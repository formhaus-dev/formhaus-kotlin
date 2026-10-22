package dev.formhaus.compose.form

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.FormRendererOptions
import dev.formhaus.compose.model.SnapshotChangeFn
import dev.formhaus.compose.model.ownedFormModel
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.engine.FormSnapshot
import dev.formhaus.core.engine.SubmitHandler
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.renderer.source

private const val SNAPSHOT_KEY = "dev.formhaus.snapshot"
private const val HOLDER_KEY = "dev.formhaus.form:"
private const val KEY_RADIX = 36

internal class FormHolder(
    private val handle: SavedStateHandle,
) : ViewModel() {
    private var model: FormModel? = null
    private var source: JsonObject? = null
    var appHandler: SnapshotChangeFn? = null

    fun model(
        source: JsonObject,
        restoring: FormSnapshot?,
        create: (FormSnapshot?, SnapshotChangeFn) -> FormModel,
    ): FormModel {
        val current = model
        if (current != null && this.source == source) return current
        val saved = if (current == null) handle.get<String>(SNAPSHOT_KEY)?.let(FormSnapshot::fromJson) else null
        current?.close()
        val created = create(restoring ?: saved, ::save)
        model = created
        this.source = source
        return created
    }

    fun release(model: FormModel) {
        if (this.model !== model) return
        model.close()
        this.model = null
        source = null
        handle.remove<String>(SNAPSHOT_KEY)
    }

    override fun onCleared() {
        model?.close()
        model = null
    }

    private fun save(snapshot: FormSnapshot?) {
        handle[SNAPSHOT_KEY] = snapshot?.toJson()
        appHandler?.invoke(snapshot)
    }
}

@Composable
internal fun rememberOwnedModel(
    definition: FormDefinition,
    initialValues: JsonObject,
    engineOptions: FormEngineOptions,
    renderer: FormRendererOptions,
    restoring: FormSnapshot?,
    onSnapshotChange: SnapshotChangeFn?,
    onSubmit: SubmitHandler,
): FormModel {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val owner = LocalViewModelStoreOwner.current
    val key = HOLDER_KEY + currentCompositeKeyHashCode.toString(KEY_RADIX)
    val holder =
        if (owner != null) {
            viewModel(owner, key) { FormHolder(createSavedStateHandle()) }
        } else {
            remember { FormHolder(SavedStateHandle()) }
        }
    val model =
        holder.model(definition.source, restoring) { start, save ->
            ownedFormModel(context, definition, initialValues, engineOptions, renderer, start, save, onSubmit)
        }
    SideEffect {
        holder.appHandler = onSnapshotChange
        model.updateHandlers(engineOptions, renderer, onSubmit)
    }
    DisposableEffect(holder, model) {
        onDispose { if (owner == null || activity?.isChangingConfigurations != true) holder.release(model) }
    }
    return model
}
