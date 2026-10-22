package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.MutableStringMap
import java.util.concurrent.atomic.AtomicInteger

private val lastToken = AtomicInteger()

internal fun freshToken(): Int = lastToken.incrementAndGet()

internal data class Ref(
    val token: Int,
    val items: List<Int>,
) {
    companion object {
        fun fresh(value: JsonValue?): Ref? =
            when (value) {
                is JsonValue.Array -> Ref(freshToken(), value.items.map { freshToken() })
                is JsonValue.Object -> Ref(freshToken(), emptyList())
                else -> null
            }

        fun preset(
            value: JsonValue,
            ordinal: Int,
        ): Ref? {
            val base = -(ordinal + 1) * 1_000_000
            return when (value) {
                is JsonValue.Array -> Ref(-(ordinal + 1), value.items.indices.map { base - it - 1 })
                is JsonValue.Object -> Ref(-(ordinal + 1), emptyList())
                else -> null
            }
        }
    }
}

internal class ValueSlot(
    val value: JsonValue?,
    ref: Ref?,
) {
    val ref: Ref? = if (value == null) null else ref

    fun isSame(other: ValueSlot): Boolean {
        val own = ref
        val theirs = other.ref
        if (own != null && theirs != null) return own.token == theirs.token
        return samePrimitive(value, other.value)
    }

    companion object {
        val EMPTY: ValueSlot = ValueSlot(null, null)

        fun fresh(value: JsonValue?): ValueSlot = ValueSlot(value, Ref.fresh(value))
    }
}

internal fun samePrimitive(
    lhs: JsonValue?,
    rhs: JsonValue?,
): Boolean =
    when {
        lhs == null || rhs == null -> lhs == null && rhs == null
        lhs is JsonValue.Array || lhs is JsonValue.Object -> false
        else -> lhs == rhs
    }

internal class Values private constructor(
    private var storage: MutableJsonObject,
    private var refs: MutableStringMap<Ref>,
) {
    private var shared = false

    constructor() : this(MutableJsonObject(), MutableStringMap())

    constructor(fresh: JsonObject) : this() {
        for ((key, value) in fresh) set(key, ValueSlot.fresh(value))
    }

    val json: JsonObject
        get() {
            shared = true
            return storage
        }

    val keys: List<String> get() = storage.keys

    operator fun get(key: String): JsonValue? = storage[key]

    fun has(key: String): Boolean = key in storage

    fun slot(key: String): ValueSlot = ValueSlot(storage[key], refs[key])

    fun copy(): Values {
        shared = true
        return Values(storage, refs).also { it.shared = true }
    }

    private fun own() {
        if (!shared) return
        storage = storage.toMutableStringMap()
        refs = refs.toMutableStringMap()
        shared = false
    }

    fun set(
        key: String,
        slot: ValueSlot,
    ) {
        val value = slot.value ?: return remove(key)
        own()
        storage[key] = value
        refs.assign(key, slot.ref ?: Ref.fresh(value))
    }

    fun remove(key: String) {
        if (key !in storage) return
        own()
        storage.remove(key)
        refs.remove(key)
    }

    fun merge(other: Values) {
        for (key in other.keys) set(key, other.slot(key))
    }

    fun itemIds(key: String): List<Int> = refs[key]?.items.orEmpty()

    fun itemId(
        key: String,
        index: Int,
    ): Int? = itemIds(key).getOrNull(index)

    fun changedKeys(other: Values): Set<String> {
        val changed = HashSet<String>()
        for (key in keys + other.keys) if (!slot(key).isSame(other.slot(key))) changed.add(key)
        return changed
    }
}
