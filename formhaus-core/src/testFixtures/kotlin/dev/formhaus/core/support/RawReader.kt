package dev.formhaus.core.support

class RawReader private constructor(
    private val bytes: ByteArray,
) {
    private var index = 0

    companion object {
        fun parse(bytes: ByteArray): RawJson? {
            val reader = RawReader(bytes)
            val value = reader.value()
            reader.skipSpace()
            return if (reader.index == bytes.size) value else null
        }

        private val simpleEscapes =
            mapOf(
                '"' to '"',
                '\\' to '\\',
                '/' to '/',
                'b' to '\b',
                'f' to '\u000C',
                'n' to '\n',
                'r' to '\r',
                't' to '\t',
            )
    }

    private fun current(): Char = bytes[index].toInt().toChar()

    private fun skipSpace() {
        while (index < bytes.size && current() in " \t\n\r") index++
    }

    private fun take(char: Char): Boolean {
        skipSpace()
        if (index >= bytes.size || current() != char) return false
        index++
        return true
    }

    private fun value(): RawJson? {
        skipSpace()
        if (index >= bytes.size) return null
        return when (current()) {
            '{' -> members()
            '[' -> elements()
            '"' -> string()?.let(RawJson::Text)
            't' -> word("true", RawJson.Bool(true))
            'f' -> word("false", RawJson.Bool(false))
            'n' -> word("null", RawJson.Null)
            else -> number()
        }
    }

    private fun members(): RawJson? {
        index++
        val members = ArrayList<Pair<String, RawJson>>()
        if (take('}')) return RawJson.Object(members)
        do {
            skipSpace()
            val key = string() ?: return null
            if (!take(':')) return null
            members.add(key to (value() ?: return null))
        } while (take(','))
        return if (take('}')) RawJson.Object(members) else null
    }

    private fun elements(): RawJson? {
        index++
        val items = ArrayList<RawJson>()
        if (take(']')) return RawJson.Array(items)
        do {
            items.add(value() ?: return null)
        } while (take(','))
        return if (take(']')) RawJson.Array(items) else null
    }

    private fun word(
        text: String,
        result: RawJson,
    ): RawJson? {
        if (index + text.length > bytes.size ||
            text.indices.any { bytes[index + it].toInt().toChar() != text[it] }
        ) {
            return null
        }
        index += text.length
        return result
    }

    private fun number(): RawJson? {
        val start = index
        while (index < bytes.size && current() in "+-.eE0123456789") index++
        return String(bytes, start, index - start, Charsets.US_ASCII).toDoubleOrNull()?.let(RawJson::Number)
    }

    private fun string(): String? {
        if (index >= bytes.size || current() != '"') return null
        index++
        val units = StringBuilder()
        var start = index
        while (index < bytes.size && current() != '"') {
            if (current() != '\\') {
                index++
                continue
            }
            units.append(String(bytes, start, index - start, Charsets.UTF_8))
            units.append(escape() ?: return null)
            start = index
        }
        if (index >= bytes.size) return null
        units.append(String(bytes, start, index - start, Charsets.UTF_8))
        index++
        return units.toString()
    }

    private fun escape(): Char? {
        if (index + 1 >= bytes.size) return null
        val kind = bytes[index + 1].toInt().toChar()
        index += 2
        simpleEscapes[kind]?.let { return it }
        if (kind != 'u' || index + 4 > bytes.size) return null
        val hex = String(bytes, index, 4, Charsets.US_ASCII)
        index += 4
        return hex.toIntOrNull(16)?.toChar()
    }
}
