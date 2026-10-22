package dev.formhaus.compose.form

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.FormRendererOptions
import dev.formhaus.compose.model.SnapshotChangeFn
import dev.formhaus.compose.model.ownedFormModel
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.engine.FormSnapshot
import dev.formhaus.core.engine.SubmitHandler
import dev.formhaus.core.engine.snapshotJson
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.renderer.source

internal const val SNAPSHOT_KEY = "dev.formhaus.snapshot"
internal const val SNAPSHOT_JSON = "json"
private const val HOLDER_KEY = "dev.formhaus.form:"
private const val KEY_RADIX = 36
internal const val MAX_SAVED_SNAPSHOT_BYTES = 96 * 1024

internal class FormHolder(
    private val handle: SavedStateHandle,
) : ViewModel() {
    private var model: FormModel? = null
    private var source: JsonObject? = null
    private var retired: FormModel? = null
    private var claimant: Any? = null

    init {
        handle.setSavedStateProvider(SNAPSHOT_KEY) { Bundle().apply { putString(SNAPSHOT_JSON, savedJson()) } }
    }

    fun claim(binding: Any): Boolean {
        if (claimant != null && claimant !== binding) return false
        claimant = binding
        return true
    }

    fun unclaim(binding: Any) {
        if (claimant === binding) claimant = null
    }

    fun model(
        source: JsonObject,
        restoring: FormSnapshot?,
        options: FormEngineOptions,
        create: (FormSnapshot?) -> FormModel,
    ): FormModel {
        val current = model
        if (current != null && this.source == source) return current
        val saved = if (current == null && restoring == null) savedSnapshot(options) else null
        if (current != null) retired = current
        val created = create(restoring ?: saved)
        model = created
        this.source = source
        return created
    }

    fun closeRetired() {
        retired?.close()
        retired = null
    }

    fun release() {
        closeRetired()
        model?.close()
        model = null
        source = null
        handle.remove<Bundle>(SNAPSHOT_KEY)
    }

    override fun onCleared() {
        closeRetired()
        model?.close()
        model = null
    }

    private fun savedSnapshot(options: FormEngineOptions): FormSnapshot? =
        handle.get<Bundle>(SNAPSHOT_KEY)?.getString(SNAPSHOT_JSON)?.let { FormSnapshot.fromJson(it, options) }

    private fun savedJson(): String? {
        val current = model ?: return null
        if (current.snapshots.cleared) return null
        return current.engine.snapshotJson(MAX_SAVED_SNAPSHOT_BYTES)
    }
}

private fun CreationExtras.savedStateHandle(): SavedStateHandle =
    if (this[SAVED_STATE_REGISTRY_OWNER_KEY] != null && this[VIEW_MODEL_STORE_OWNER_KEY] != null) {
        createSavedStateHandle()
    } else {
        SavedStateHandle()
    }

private class Binding {
    var holder: FormHolder? = null
}

private fun claimedHolder(
    owner: ViewModelStoreOwner,
    base: String,
    binding: Binding,
): FormHolder {
    val extras = (owner as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras ?: CreationExtras.Empty
    val provider = ViewModelProvider.create(owner, viewModelFactory { initializer { FormHolder(savedStateHandle()) } }, extras)
    var index = 0
    while (true) {
        val holder = provider["$base#$index", FormHolder::class]
        if (holder.claim(binding)) return holder
        index += 1
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
    val owner = LocalViewModelStoreOwner.current
    val binding = remember { Binding() }
    val base = HOLDER_KEY + definition.source["id"]?.stringValue.orEmpty() + ":" + currentCompositeKeyHashCode.toString(KEY_RADIX)
    val holder =
        remember(owner, base) {
            if (owner != null) claimedHolder(owner, base, binding) else FormHolder(SavedStateHandle()).also { it.claim(binding) }
        }
    val model =
        holder.model(definition.source, restoring, engineOptions) { start ->
            ownedFormModel(context, definition, initialValues, engineOptions, renderer, start, onSnapshotChange, onSubmit)
        }
    SideEffect {
        val previous = binding.holder
        if (previous != null && previous !== holder) previous.release()
        binding.holder = holder
        holder.closeRetired()
        model.snapshots.handler = onSnapshotChange
        model.updateHandlers(engineOptions, renderer, onSubmit)
    }
    DisposableEffect(holder) {
        onDispose {
            holder.unclaim(binding)
            if (owner == null) holder.release()
        }
    }
    return model
}
