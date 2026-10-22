package dev.formhaus.core.regex

internal class PatternCache(
    private val limit: Int = LIMIT,
    private val byteLimit: Int = BYTE_LIMIT,
) {
    private class Entry(
        val pattern: CompiledPattern?,
        val bytes: Int,
    )

    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private var used = 0

    val bytes: Int get() = synchronized(this) { used }

    fun pattern(source: String): CompiledPattern? {
        synchronized(this) { entries[source]?.let { return it.pattern } }
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
        if (source in entries || size > byteLimit) return
        val oldest = entries.values.iterator()
        while (oldest.hasNext() && (entries.size >= limit || used + size > byteLimit)) {
            val evicted = oldest.next()
            used -= evicted.bytes
            oldest.remove()
            evicted.pattern?.release()
        }
        entries[source] = Entry(pattern, size)
        used += size
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
