package dev.formhaus.compose.fields

private const val NAME_BYTES = 200
private const val EXTENSION_LIMIT = 16
private const val ONE_BYTE = 0x80
private const val TWO_BYTES = 0x800
private const val THREE_BYTES = 0x10000

internal fun uploadName(raw: String): String {
    val clean =
        raw
            .substringAfterLast('/')
            .filterNot { it.isISOControl() }
            .trim()
    val name = if (clean.isEmpty() || clean == "." || clean == "..") "file" else clean
    if (utf8Size(name) <= NAME_BYTES) return name
    val dot = name.lastIndexOf('.')
    val extension = if (dot > 0 && name.length - dot <= EXTENSION_LIMIT) name.substring(dot) else ""
    return fit(name.dropLast(extension.length), NAME_BYTES - utf8Size(extension)) + extension
}

private fun fit(
    text: String,
    bytes: Int,
): String {
    var used = 0
    var end = 0
    while (end < text.length) {
        val point = text.codePointAt(end)
        used += utf8Size(point)
        if (used > bytes) break
        end += Character.charCount(point)
    }
    return text.substring(0, end)
}

private fun utf8Size(text: String): Int = text.toByteArray(Charsets.UTF_8).size

private fun utf8Size(point: Int): Int =
    when {
        point < ONE_BYTE -> 1
        point < TWO_BYTES -> 2
        point < THREE_BYTES -> 3
        else -> 4
    }
