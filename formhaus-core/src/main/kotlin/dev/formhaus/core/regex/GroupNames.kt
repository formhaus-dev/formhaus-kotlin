package dev.formhaus.core.regex

internal class CaptureScan(
    units: CharArray,
) {
    val names: List<String?>

    init {
        val names = ArrayList<String?>()
        var index = 0
        var inClass = false
        while (index < units.size) {
            val unit = units[index]
            if (unit == '\\') {
                index += 2
                continue
            }
            if (inClass) {
                inClass = unit != ']'
            } else if (unit == '[') {
                inClass = true
            } else if (unit == '(') {
                groupStart(units, index + 1)?.let { names.add(it.name) }
            }
            index++
        }
        this.names = names
    }

    val hasNames: Boolean = names.any { it != null }

    fun indices(name: String): List<Int> = names.indices.filter { names[it] == name }.map { it + 1 }

    private class Start(
        val name: String?,
    )

    private fun groupStart(
        units: CharArray,
        index: Int,
    ): Start? {
        if (index >= units.size || units[index] != '?') return Start(null)
        if (index + 2 >= units.size || units[index + 1] != '<' || units[index + 2] == '=' ||
            units[index + 2] == '!'
        ) {
            return null
        }
        return Start(parseGroupName(units, index + 2)?.name ?: "")
    }
}

internal class NameScope private constructor(
    private val start: Int,
    private val lower: Int,
    private val upper: Int,
) {
    constructor(start: Int) : this(start, 0, 0)

    fun opening(start: Int): NameScope = NameScope(start, lower, upper)

    fun addingAlternative(captures: Int): NameScope = NameScope(start, if (upper == 0) start else lower, captures)

    fun admitsDuplicate(index: Int): Boolean = index in lower..upper
}

internal class GroupName(
    val name: String,
    val end: Int,
)

internal fun parseGroupName(
    units: CharArray,
    start: Int,
): GroupName? {
    var index = start
    val name = StringBuilder()
    while (index < units.size) {
        if (units[index] == '>') return if (name.isEmpty()) null else GroupName(name.toString(), index + 1)
        val scalar = nameScalar(units, index) ?: return null
        if (!isNameScalar(scalar.value, name.isEmpty())) return null
        name.appendCodePoint(scalar.value)
        index = scalar.end
    }
    return null
}

internal fun hexValue(
    units: CharArray,
    index: Int,
    count: Int,
): Int? {
    if (index + count > units.size) return null
    var value = 0
    for (offset in 0 until count) {
        val digit = Character.digit(units[index + offset].code.takeIf { it < 128 } ?: return null, 16)
        if (digit < 0) return null
        value = value * 16 + digit
    }
    return value
}

private class Scalar(
    val value: Int,
    val end: Int,
)

private fun isNameScalar(
    scalar: Int,
    start: Boolean,
): Boolean {
    if (scalar == '$'.code || scalar == '_'.code) return true
    if (start) return Character.isUnicodeIdentifierStart(scalar)
    return scalar == 0x200C || scalar == 0x200D ||
        (Character.isUnicodeIdentifierPart(scalar) && !Character.isIdentifierIgnorable(scalar))
}

private fun isLead(unit: Int): Boolean = unit in 0xD800..0xDBFF

private fun isTrail(unit: Int): Boolean = unit in 0xDC00..0xDFFF

private fun isScalar(value: Int): Boolean = value in 0..0x10FFFF && !isLead(value) && !isTrail(value)

private fun combine(
    lead: Int,
    trail: Int,
): Int = 0x10000 + ((lead - 0xD800) shl 10) + (trail - 0xDC00)

private fun nameScalar(
    units: CharArray,
    index: Int,
): Scalar? {
    val unit = units[index].code
    if (unit == '\\'.code) return escapedScalar(units, index + 1)
    if (isLead(unit) && index + 1 < units.size && isTrail(units[index + 1].code)) {
        return Scalar(combine(unit, units[index + 1].code), index + 2)
    }
    return if (isScalar(unit)) Scalar(unit, index + 1) else null
}

private fun escapedScalar(
    units: CharArray,
    index: Int,
): Scalar? {
    if (index >= units.size || units[index] != 'u') return null
    if (index + 1 < units.size && units[index + 1] == '{') return bracedScalar(units, index + 2)
    val lead = hexValue(units, index + 1, 4) ?: return null
    val rest = index + 5
    if (isLead(lead) && rest + 1 < units.size && units[rest] == '\\' && units[rest + 1] == 'u') {
        val trail = hexValue(units, rest + 2, 4)
        if (trail != null && isTrail(trail)) return Scalar(combine(lead, trail), rest + 6)
    }
    return if (isScalar(lead)) Scalar(lead, rest) else null
}

private fun bracedScalar(
    units: CharArray,
    start: Int,
): Scalar? {
    var index = start
    var value = 0
    while (index < units.size) {
        val digit = hexValue(units, index, 1) ?: break
        value = minOf(value * 16 + digit, 0x110000)
        index++
    }
    if (index == start || index >= units.size || units[index] != '}') return null
    return if (isScalar(value)) Scalar(value, index + 1) else null
}
