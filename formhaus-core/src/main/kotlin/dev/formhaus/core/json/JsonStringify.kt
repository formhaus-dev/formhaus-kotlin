package dev.formhaus.core.json

public fun JsonValue.toJsonString(): String = jsonStringify(this)

public fun JsonValue.toJsonBytes(): ByteArray = toJsonString().toByteArray(Charsets.UTF_8)

public fun JsonObject.toJsonString(): String = JsonValue.Object(this).toJsonString()

public fun JsonObject.toJsonBytes(): ByteArray = JsonValue.Object(this).toJsonBytes()

private sealed interface Piece {
    data class Text(
        val text: String,
    ) : Piece

    data class Value(
        val value: JsonValue,
    ) : Piece
}

private fun scalarString(value: JsonValue): String =
    when (value) {
        JsonValue.Null -> "null"
        is JsonValue.Bool -> if (value.value) "true" else "false"
        is JsonValue.Number -> jsNumberString(value.value)
        is JsonValue.String -> value.value
        is JsonValue.Array, is JsonValue.Object -> "[object Object]"
    }

private fun ArrayList<Piece>.pushItems(items: List<JsonValue>) {
    for (index in items.indices.reversed()) {
        add(Piece.Value(items[index]))
        if (index > 0) add(Piece.Text(","))
    }
}

internal fun jsString(value: JsonValue?): String {
    if (value == null) return "undefined"
    if (value !is JsonValue.Array) return scalarString(value)
    val output = StringBuilder()
    val pending = arrayListOf<Piece>(Piece.Value(value))
    while (pending.isNotEmpty()) {
        when (val piece = pending.removeAt(pending.lastIndex)) {
            is Piece.Text -> {
                output.append(piece.text)
            }

            is Piece.Value -> {
                when (val item = piece.value) {
                    JsonValue.Null -> Unit
                    is JsonValue.Array -> pending.pushItems(item.items)
                    else -> output.append(scalarString(item))
                }
            }
        }
    }
    return output.toString()
}

internal fun jsonStringify(value: JsonValue?): String {
    if (value == null) return "undefined"
    val output = StringBuilder()
    val pending = arrayListOf<Piece>(Piece.Value(value))
    while (pending.isNotEmpty()) {
        when (val piece = pending.removeAt(pending.lastIndex)) {
            is Piece.Text -> output.append(piece.text)
            is Piece.Value -> stringifyValue(piece.value, output, pending)
        }
    }
    return output.toString()
}

private fun stringifyValue(
    item: JsonValue,
    output: StringBuilder,
    pending: ArrayList<Piece>,
) {
    when (item) {
        is JsonValue.Number -> {
            output.append(if (item.value.isFinite()) jsNumberString(item.value) else "null")
        }

        is JsonValue.String -> {
            appendQuoted(output, item.value)
        }

        is JsonValue.Array -> {
            output.append('[')
            pending.add(Piece.Text("]"))
            pending.pushItems(item.items)
        }

        is JsonValue.Object -> {
            output.append('{')
            pending.add(Piece.Text("}"))
            val entries = item.members.toList()
            for (index in entries.indices.reversed()) {
                val (key, member) = entries[index]
                pending.add(Piece.Value(member))
                pending.add(Piece.Text(quoteJson(key) + ":"))
                if (index > 0) pending.add(Piece.Text(","))
            }
        }

        else -> {
            output.append(scalarString(item))
        }
    }
}

internal fun quoteJson(text: String): String = StringBuilder(text.length + 2).also { appendQuoted(it, text) }.toString()

private fun appendQuoted(
    output: StringBuilder,
    text: String,
) {
    output.append('"')
    for ((index, char) in text.withIndex()) {
        when (char) {
            '"' -> {
                output.append("\\\"")
            }

            '\\' -> {
                output.append("\\\\")
            }

            '\n' -> {
                output.append("\\n")
            }

            '\r' -> {
                output.append("\\r")
            }

            '\t' -> {
                output.append("\\t")
            }

            '\b' -> {
                output.append("\\b")
            }

            '\u000C' -> {
                output.append("\\f")
            }

            else -> {
                if (char < ' ' || isLoneSurrogate(text, index)) {
                    output.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else {
                    output.append(char)
                }
            }
        }
    }
    output.append('"')
}

private fun isLoneSurrogate(
    text: String,
    index: Int,
): Boolean {
    val char = text[index]
    return when {
        char.isHighSurrogate() -> index + 1 >= text.length || !text[index + 1].isLowSurrogate()
        char.isLowSurrogate() -> index == 0 || !text[index - 1].isHighSurrogate()
        else -> false
    }
}
