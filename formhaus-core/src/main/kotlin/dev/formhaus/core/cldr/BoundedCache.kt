package dev.formhaus.core.cldr

import java.util.concurrent.ConcurrentHashMap

internal class BoundedCache<K : Any, V : Any>(
    private val limit: Int,
) {
    private val entries = ConcurrentHashMap<K, V>()

    fun getOrPut(
        key: K,
        create: (K) -> V,
    ): V {
        entries[key]?.let { return it }
        if (entries.size >= limit) entries.clear()
        return create(key).also { entries[key] = it }
    }
}
