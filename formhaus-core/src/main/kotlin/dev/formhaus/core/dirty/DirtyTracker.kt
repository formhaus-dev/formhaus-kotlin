package dev.formhaus.core.dirty

import dev.formhaus.core.engine.EngineCore
import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.HiddenValues
import dev.formhaus.core.engine.Listeners
import dev.formhaus.core.engine.getSubmitValues
import dev.formhaus.core.engine.replaceDefinition
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.model.FormDefinition

private class Baseline(
    val definition: FormDefinition,
    val values: JsonObject,
    val skipped: List<Pair<String, List<String>>>,
) {
    var compared: FormDefinition? = null
    var payload: JsonObject? = null
}

private class Inputs(
    val definition: FormDefinition,
    val values: JsonObject,
    val skipped: List<Pair<String, List<String>>>,
) {
    fun same(other: Inputs?): Boolean =
        other != null && other.definition === definition && other.values === values && other.skipped == skipped
}

public class DirtyTracker internal constructor(
    private val engine: FormEngine,
) {
    private val listeners = Listeners()
    private var baseline = capture()
    private var resetCount = engine.resetCount
    private var seen: Inputs? = null
    private var stale = true
    private var stopped = false
    private var keys: List<String> = emptyList()
    private val unsubscribe: () -> Unit = engine.subscribe(::changed)

    public val dirtyKeys: List<String>
        get() {
            refresh()
            return keys
        }

    public val dirty: Boolean get() = dirtyKeys.isNotEmpty()

    public fun markClean() {
        baseline = capture()
        resetCount = engine.resetCount
        seen = inputs()
        stale = false
        publish(emptyList())
    }

    public fun subscribe(listener: () -> Unit): () -> Unit {
        val id = listeners.add(listener)
        refresh()
        return { listeners.remove(id) }
    }

    public fun stop() {
        stopped = true
        unsubscribe()
    }

    private fun capture(): Baseline = Baseline(engine.definition, engine.values, skipped())

    private fun skipped(): List<Pair<String, List<String>>> = engine.core.skipped.map { it.id to it.keys }

    private fun inputs(): Inputs = Inputs(engine.definition, engine.values, skipped())

    private fun changed() {
        if (resetCount != engine.resetCount) {
            baseline = capture()
            resetCount = engine.resetCount
            seen = null
        }
        stale = true
        if (!listeners.isEmpty) refresh()
    }

    private fun refresh() {
        if (!stale || stopped) return
        stale = false
        val current = inputs()
        if (current.same(seen)) return
        seen = current
        publish(changedKeys(payload(current.definition), engine.getSubmitValues()))
    }

    private fun payload(definition: FormDefinition): JsonObject {
        val known = baseline.payload
        if (known != null && baseline.compared === definition) return known
        val options = engine.options
        val shadow =
            EngineCore(baseline.definition, baseline.values, options.validators, keep = options.hiddenValues == HiddenValues.KEEP)
        for ((id, keys) in baseline.skipped) shadow.markSkipped(id, keys)
        if (definition !== baseline.definition) shadow.replaceDefinition(definition)
        return shadow.getSubmitValues(payload = true).also {
            baseline.compared = definition
            baseline.payload = it
        }
    }

    private fun publish(next: List<String>) {
        if (next == keys) return
        keys = next
        listeners.fire()
    }
}

public fun FormEngine.trackDirty(): DirtyTracker = DirtyTracker(this)
