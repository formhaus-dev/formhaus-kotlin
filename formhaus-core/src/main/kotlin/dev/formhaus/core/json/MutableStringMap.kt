package dev.formhaus.core.json

public class MutableStringMap<V : Any> internal constructor(insertionOrder: Boolean) : StringMap<V>(insertionOrder) {
    public constructor() : this(false)

    public operator fun set(key: String, value: V) {
        write(key, value)
    }

    public fun remove(key: String): V? = delete(key)

    internal fun getOrPut(key: String, fallback: () -> V): V = get(key) ?: fallback().also { write(key, it) }

    internal fun merge(other: StringMap<V>) {
        for ((key, value) in other) write(key, value)
    }
}

public fun <V : Any> stringMapOf(vararg pairs: Pair<String, V>): StringMap<V> = mutableStringMapOf(*pairs)

public fun <V : Any> mutableStringMapOf(vararg pairs: Pair<String, V>): MutableStringMap<V> =
    MutableStringMap<V>().also { map -> for ((key, value) in pairs) map[key] = value }
