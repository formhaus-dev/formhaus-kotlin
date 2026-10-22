package dev.formhaus.core.renderer

import dev.formhaus.core.json.jsTrimmed
import dev.formhaus.core.regex.JsRegex

private val specialSchemes = setOf("ftp", "file", "http", "https", "ws", "wss")
private val rasterData = JsRegex.unchecked("^image\\/(?:png|jpeg|webp|gif)[;,]")
private val removed = setOf('\t'.code, '\n'.code, '\r'.code)
private val specialEnds = setOf('/'.code, '?'.code, '#'.code, '\\'.code)
private val plainEnds = setOf('/'.code, '?'.code, '#'.code)
private const val AT = '@'.code

private fun isAsciiLetter(point: Int): Boolean = point in 'a'.code..'z'.code || point in 'A'.code..'Z'.code

private fun isScheme(name: List<Int>): Boolean =
    name.isNotEmpty() && isAsciiLetter(name[0]) &&
        name.all { isAsciiLetter(it) || it in '0'.code..'9'.code || it == '+'.code || it == '-'.code || it == '.'.code }

private fun text(points: List<Int>): String = buildString { points.forEach(::appendCodePoint) }

private class ImageUrl(
    source: String,
) {
    var scheme = "https"
        private set
    var authority: List<Int>? = null
        private set
    var path: List<Int>
        private set
    private var rest: List<Int>

    init {
        val kept =
            source
                .codePoints()
                .filter { it !in removed }
                .toArray()
                .asList()
        val start = kept.indexOfFirst { it > 0x20 }.takeIf { it >= 0 } ?: kept.size
        val end = maxOf(start, kept.indexOfLast { it > 0x20 } + 1)
        rest = kept.subList(start, end)
        var relative = true
        val colon = rest.indexOf(':'.code)
        if (colon >= 0 && isScheme(rest.subList(0, colon))) {
            scheme = text(rest.subList(0, colon)).lowercase()
            rest = rest.subList(colon + 1, rest.size)
            relative = scheme == "https"
        }
        val special = scheme in specialSchemes
        val slashes = rest.takeWhile { if (special) it == '/'.code || it == '\\'.code else it == '/'.code }.size
        if ((special && !relative) || slashes >= 2) {
            val after = rest.subList(if (special) slashes else 2, rest.size)
            val ends = if (special) specialEnds else plainEnds
            val host = after.takeWhile { it !in ends }
            authority = host
            path = after.subList(host.size, after.size)
        } else {
            path = rest
        }
    }

    val credentials: Boolean
        get() {
            val authority = authority ?: return false
            val at = authority.lastIndexOf(AT)
            if (at < 0) return false
            val user = authority.subList(0, at)
            return user.isNotEmpty() && user != listOf(':'.code)
        }

    val rfcUserInfo: Boolean
        get() {
            if (rest.size < 2 || rest[0] != '/'.code || rest[1] != '/'.code) return false
            return rest.drop(2).takeWhile { it !in plainEnds }.contains(AT)
        }

    val emptyHost: Boolean
        get() {
            val authority = authority ?: return false
            val at = authority.lastIndexOf(AT)
            return if (at >= 0) at + 1 == authority.size else authority.isEmpty()
        }
}

@RendererApi
public fun imageSource(image: String?): String? {
    if (image == null || image.jsTrimmed.isEmpty()) return null
    val url = ImageUrl(image)
    if (url.credentials || url.rfcUserInfo) return null
    return when (url.scheme) {
        "http", "https" -> if (url.emptyHost) null else image
        "blob" -> image
        "data" -> if (rasterData.test(text(url.path).lowercase()) == true) image else null
        else -> null
    }
}
