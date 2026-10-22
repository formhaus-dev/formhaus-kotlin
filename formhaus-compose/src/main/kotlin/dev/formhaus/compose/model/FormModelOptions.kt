package dev.formhaus.compose.model

import dev.formhaus.core.engine.optionKey
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.model.FormField
import dev.formhaus.core.renderer.optionKey
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

public fun FormModel.options(key: String): List<FieldOption>? = optionsState(engine.optionKey(key)).options

private fun FormModel.optionsState(key: String): OptionsState = optionStates.getOrPut(key, ::OptionsState)

internal fun FormModel.reloadOptions() {
    if (options.optionsProviders.isEmpty()) return
    val revision = structure.revision
    val replaced = revision != optionsRevision
    optionsRevision = revision
    val active = mutableSetOf<String>()
    for ((key, field, name) in optionSources()) {
        val provider = options.optionsProviders[name] ?: continue
        val state = optionsState(key)
        if (replaced) active += key
        val source = OptionsSource(name, revision, field.optionsDependsOn.orEmpty().map { engine.values[it] })
        if (state.source != source) load(state, source, provider)
    }
    if (!replaced) return
    for ((key, state) in optionStates) if (key !in active) state.cancel()
}

private fun FormModel.load(
    state: OptionsState,
    source: OptionsSource,
    provider: OptionsProvider,
) {
    if (state.source?.provider != source.provider) state.update(null)
    state.source = source
    state.request += 1
    val request = state.request
    val values = engine.values
    scope.launch {
        val loaded =
            try {
                provider(values)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (state.request == request) reportError(error)
                null
            }
        if (state.request == request) state.update(loaded)
    }
}

private fun FormModel.optionSources(): List<Triple<String, FormField, String>> =
    structure.visibleFields
        .flatMap { field ->
            if (field.isGroup) field.items.map { optionKey(field.key, it.key) to it } else listOf(field.key to field)
        }.mapNotNull { (key, field) -> field.optionsFrom?.stringValue?.let { Triple(key, field, it) } }
