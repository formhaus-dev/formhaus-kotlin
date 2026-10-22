package dev.formhaus.core.scenario

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield

class ScriptError(message: String) : Exception(message)

class Script {
    private class Deferred(val name: String, var release: () -> Unit)

    private val deferred = ArrayList<Deferred>()

    val pending: List<String> get() = deferred.map { it.name }

    suspend fun reply(entry: JsonValue?): JsonValue? {
        val reply = entry?.objectValue ?: return null
        val name = reply["defer"]?.stringValue ?: return settle(reply)
        return suspendCancellableCoroutine { continuation ->
            val release = { continuation.resumeWith(runCatching { settle(reply) }) }
            val known = deferred.firstOrNull { it.name == name }
            if (known != null) known.release = release else deferred.add(Deferred(name, release))
        }
    }

    private fun settle(entry: JsonObject): JsonValue? {
        entry["throw"]?.let { throw ScriptError(it.stringValue.orEmpty()) }
        return entry["return"]
    }

    fun resolve(name: String) {
        val index = deferred.indexOfFirst { it.name == name }
        if (index < 0) throw ScriptError("No pending deferred reply \"$name\"")
        deferred.removeAt(index).release()
    }
}

class Replies(entries: List<JsonValue>) {
    private val entries = ArrayDeque(entries)

    fun next(): JsonValue? = entries.removeFirstOrNull()
}

class EventLog {
    var entries: MutableList<JsonValue> = ArrayList()

    fun record(vararg pairs: Pair<String, JsonValue>) {
        entries.add(obj(*pairs))
    }

    fun drain(): List<JsonValue> = entries.also { entries = ArrayList() }
}

suspend fun drain() {
    repeat(16) { yield() }
}
