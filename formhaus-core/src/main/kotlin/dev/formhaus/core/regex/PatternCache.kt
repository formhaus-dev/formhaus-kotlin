package dev.formhaus.core.regex

import java.util.concurrent.ConcurrentHashMap

internal class PatternCache(
    private val limit: Int = LIMIT,
    private val byteLimit: Int = BYTE_LIMIT,
) {
    private class Entry(
        val pattern: CompiledPattern?,
        val bytes: Int,
    ) {
        var used = false
    }

    private val entries = ConcurrentHashMap<String, Entry>()
    private val order = ArrayDeque<String>()
    private var total = 0

    val bytes: Int get() = synchronized(this) { total }

    fun pattern(source: String): CompiledPattern? {
        entries[source]?.let { entry ->
            if (!entry.used) entry.used = true
            return entry.pattern
        }
        val compiled =
            try {
                CompiledPattern(source)
            } catch (_: RegexSyntaxError) {
                null
            }
        synchronized(this) { insert(source, compiled) }
        return compiled
    }

    private fun insert(
        source: String,
        pattern: CompiledPattern?,
    ) {
        val size = utf8Length(source) + (pattern?.bytes ?: 0)
        if (entries.containsKey(source) || size > byteLimit) return
        while (order.isNotEmpty() && (entries.size >= limit || total + size > byteLimit)) evictColdest()
        entries[source] = Entry(pattern, size)
        order.addLast(source)
        total += size
    }

    private fun evictColdest() {
        while (true) {
            val source = order.removeFirst()
            val entry = entries.getValue(source)
            if (entry.used) {
                entry.used = false
                order.addLast(source)
                continue
            }
            entries.remove(source)
            total -= entry.bytes
            entry.pattern?.release()
            return
        }
    }

    companion object {
        const val LIMIT: Int = 256
        const val BYTE_LIMIT: Int = 16 shl 20
        val shared: PatternCache = PatternCache()

        private fun utf8Length(text: String): Int {
            var length = 0
            var index = 0
            while (index < text.length) {
                val char = text[index]
                val pair = char.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()
                length +=
                    when {
                        pair -> 4
                        char.code < 0x80 -> 1
                        char.code < 0x800 -> 2
                        else -> 3
                    }
                index += if (pair) 2 else 1
            }
            return length
        }
    }
}
