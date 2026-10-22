package dev.formhaus.core.validation

import dev.formhaus.core.json.jsTrimmed
import dev.formhaus.core.unicode.isLetterMarkNumber

internal fun cut(text: String): String {
    if (text.length <= MAX_STRING_LENGTH) return text
    val end = if (text[MAX_STRING_LENGTH - 1].isHighSurrogate()) MAX_STRING_LENGTH - 1 else MAX_STRING_LENGTH
    return text.substring(0, end)
}

internal sealed class TemplateToken {
    data object Escape : TemplateToken()

    data class Param(
        val name: String,
    ) : TemplateToken()

    data class Recall(
        val key: String?,
    ) : TemplateToken()
}

private class FoundToken(
    val end: Int,
    val token: TemplateToken,
)

private fun isWordChar(char: Char): Boolean = char in '0'..'9' || char in 'A'..'Z' || char in 'a'..'z' || char == '_'

private fun isKeyCodePoint(point: Int): Boolean = isLetterMarkNumber(point) || point == '_'.code || point == '.'.code || point == '-'.code

internal fun recallKey(content: String): String? {
    val key = content.jsTrimmed
    return key.takeIf { it.isNotEmpty() && it.codePoints().allMatch(::isKeyCodePoint) }
}

internal fun hasRecallBraces(text: String): Boolean = "{{" in text

private fun tokenAt(
    text: String,
    start: Int,
): FoundToken? {
    val next = start + 1
    if (text[start] == '\\') {
        return if (text.startsWith("{{", next)) FoundToken(next + 2, TemplateToken.Escape) else null
    }
    if (next < text.length && text[next] == '{') {
        val body = next + 1
        var stop = body
        while (stop < text.length && text[stop] != '{' && text[stop] != '}') stop++
        if (text.startsWith(
                "}}",
                stop,
            )
        ) {
            return FoundToken(stop + 2, TemplateToken.Recall(recallKey(text.substring(body, stop))))
        }
    }
    var stop = next
    while (stop < text.length && isWordChar(text[stop])) stop++
    if (stop == next || stop >= text.length || text[stop] != '}') return null
    return FoundToken(stop + 1, TemplateToken.Param(text.substring(next, stop)))
}

internal fun forEachToken(
    text: String,
    body: (start: Int, end: Int, token: TemplateToken) -> Boolean,
) {
    var index = 0
    while (index < text.length) {
        val start = (index until text.length).firstOrNull { text[it] == '{' || text[it] == '\\' } ?: return
        val found = tokenAt(text, start)
        if (found == null) {
            index = start + 1
            continue
        }
        if (!body(start, found.end, found.token)) return
        index = found.end
    }
}

internal fun fillTemplate(
    text: String,
    replace: (raw: String, token: TemplateToken) -> String,
): String {
    val result = StringBuilder()
    var last = 0
    forEachToken(text) { start, end, token ->
        if (result.length > MAX_STRING_LENGTH) return@forEachToken false
        result.append(text, last, start).append(replace(text.substring(start, end), token))
        last = end
        true
    }
    result.append(text, last, text.length)
    return cut(result.toString())
}
