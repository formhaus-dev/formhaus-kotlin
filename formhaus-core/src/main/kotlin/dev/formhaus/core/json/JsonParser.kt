package dev.formhaus.core.json

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

public class JsonParseException(public val offset: Int, public val reason: String) :
    Exception("Invalid JSON at byte $offset: $reason")

private val replacementBytes = byteArrayOf(0xEF.toByte(), 0xBF.toByte(), 0xBD.toByte())

internal fun utf8Bytes(text: String): ByteArray {
    val encoder = Charsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
        .replaceWith(replacementBytes)
    val buffer = encoder.encode(CharBuffer.wrap(text))
    return ByteArray(buffer.remaining()).also { buffer.get(it) }
}

private class Frames {
    val isArray = ArrayList<Boolean>()
    val arrays = ArrayList<ArrayList<JsonValue>>()
    val objects = ArrayList<MutableJsonObject>()
    val keys = ArrayList<String>()

    val depth: Int get() = isArray.size

    fun openArray() {
        isArray.add(true)
        arrays.add(ArrayList())
    }

    fun openObject(key: String) {
        isArray.add(false)
        objects.add(MutableJsonObject())
        keys.add(key)
    }

    fun closeArray(): JsonValue {
        isArray.removeLast()
        return JsonValue.Array(arrays.removeLast())
    }

    fun closeObject(): JsonValue {
        isArray.removeLast()
        return JsonValue.Object(objects.removeLast().also { it.establishKeyOrder() })
    }
}

private fun isSpace(byte: Byte): Boolean = byte == SPACE || byte == TAB || byte == NEWLINE || byte == RETURN

private const val SPACE: Byte = 0x20
private const val TAB: Byte = 0x09
private const val NEWLINE: Byte = 0x0A
private const val RETURN: Byte = 0x0D
private const val QUOTE = '"'.code.toByte()
private const val BACKSLASH = '\\'.code.toByte()

internal class JsonParser(private val bytes: ByteArray) {
    private var index = 0

    fun parseDocument(): JsonValue {
        val frames = Frames()
        parsing@ while (true) {
            var value = openValue(frames) ?: continue
            while (frames.depth > 0) {
                skipWhitespace()
                if (index >= bytes.size) throw fail("unexpected end of input")
                val byte = bytes[index++].toInt().toChar()
                if (frames.isArray.last()) {
                    frames.arrays.last().add(value)
                    if (byte == ',') continue@parsing
                    if (byte != ']') throw fail("expected ',' or ']'")
                    value = frames.closeArray()
                } else {
                    frames.objects.last().appendParsed(frames.keys.removeLast(), value)
                    if (byte == ',') {
                        frames.keys.add(parseKey())
                        continue@parsing
                    }
                    if (byte != '}') throw fail("expected ',' or '}'")
                    value = frames.closeObject()
                }
            }
            skipWhitespace()
            if (index != bytes.size) throw fail("unexpected trailing content")
            return value
        }
    }

    private fun fail(reason: String) = JsonParseException(index, reason)

    private fun skipWhitespace() {
        while (index < bytes.size && isSpace(bytes[index])) index++
    }

    private fun peek(char: Char): Boolean = index < bytes.size && bytes[index] == char.code.toByte()

    private fun openValue(frames: Frames): JsonValue? {
        skipWhitespace()
        if (index >= bytes.size) throw fail("unexpected end of input")
        val byte = bytes[index].toInt().toChar()
        if ((byte == '[' || byte == '{') && frames.depth >= JsonValue.MAX_NESTING_DEPTH) {
            throw fail("nesting deeper than ${JsonValue.MAX_NESTING_DEPTH} levels")
        }
        return when (byte) {
            '[' -> {
                index++
                skipWhitespace()
                if (peek(']')) {
                    index++
                    JsonValue.Array(emptyList())
                } else {
                    frames.openArray()
                    null
                }
            }
            '{' -> {
                index++
                skipWhitespace()
                if (peek('}')) {
                    index++
                    JsonValue.Object(MutableJsonObject())
                } else {
                    frames.openObject(parseKey())
                    null
                }
            }
            '"' -> JsonValue.String(parseString())
            't' -> literal("true", JsonValue.Bool(true))
            'f' -> literal("false", JsonValue.Bool(false))
            'n' -> literal("null", JsonValue.Null)
            else -> JsonValue.Number(parseNumber())
        }
    }

    private fun parseKey(): String {
        skipWhitespace()
        if (!peek('"')) throw fail("expected a property name")
        val key = parseString()
        skipWhitespace()
        if (!peek(':')) throw fail("expected ':'")
        index++
        return key
    }

    private fun literal(word: String, value: JsonValue): JsonValue {
        if (index + word.length > bytes.size || word.indices.any { bytes[index + it] != word[it].code.toByte() }) {
            throw fail("unexpected token")
        }
        index += word.length
        return value
    }

    private fun parseString(): String {
        index++
        val units = StringBuilder()
        while (true) {
            if (index >= bytes.size) throw fail("unterminated string")
            val byte = bytes[index]
            if (byte == QUOTE) {
                index++
                return units.toString()
            }
            if (byte in 0..0x1F) throw fail("control character in string")
            if (byte == BACKSLASH) {
                appendEscape(units)
                continue
            }
            val start = index
            while (index < bytes.size && bytes[index] != QUOTE && bytes[index] != BACKSLASH && bytes[index] !in 0..0x1F) index++
            units.append(String(bytes, start, index - start, Charsets.UTF_8))
        }
    }

    private fun appendEscape(units: StringBuilder) {
        val escaped = parseEscape()
        if (escaped.isLowSurrogate()) throw fail("lone surrogate in string")
        units.append(escaped)
        if (!escaped.isHighSurrogate()) return
        if (!peek('\\')) throw fail("lone surrogate in string")
        val trail = parseEscape()
        if (!trail.isLowSurrogate()) throw fail("lone surrogate in string")
        units.append(trail)
    }

    private fun parseEscape(): Char {
        index++
        if (index >= bytes.size) throw fail("unterminated escape")
        return when (bytes[index++].toInt().toChar()) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> parseUnicodeEscape()
            else -> throw fail("invalid escape")
        }
    }

    private fun parseUnicodeEscape(): Char {
        if (index + 4 > bytes.size) throw fail("invalid unicode escape")
        var unit = 0
        for (offset in 0 until 4) {
            val digit = asciiDigit(bytes[index + offset].toInt().toChar(), 16)
            if (digit < 0) throw fail("invalid unicode escape")
            unit = unit * 16 + digit
        }
        index += 4
        return unit.toChar()
    }

    private fun digits(): Int {
        val from = index
        while (index < bytes.size && bytes[index] in '0'.code.toByte()..'9'.code.toByte()) index++
        return index - from
    }

    private fun parseNumber(): Double {
        val start = index
        if (peek('-')) index++
        val integer = index
        if (digits() == 0) throw fail("unexpected token")
        if (bytes[integer] == '0'.code.toByte() && index - integer > 1) throw fail("leading zero")
        if (peek('.')) {
            index++
            if (digits() == 0) throw fail("expected digits")
        }
        if (peek('e') || peek('E')) {
            index++
            if (peek('+') || peek('-')) index++
            if (digits() == 0) throw fail("expected exponent digits")
        }
        return String(bytes, start, index - start, Charsets.US_ASCII).toDouble()
    }
}
