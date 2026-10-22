package dev.formhaus.core.regex

import java.util.Locale

internal object CaseFolding {
    private val newerUppercase = mapOf(
        '\u019B' to '\uA7DC', '\u0264' to '\uA7CB', '\u1C8A' to '\u1C89', '\u2C5F' to '\u2C2F', '\uA7C1' to '\uA7C0',
        '\uA7CD' to '\uA7CC', '\uA7CF' to '\uA7CE', '\uA7D1' to '\uA7D0', '\uA7D3' to '\uA7D2', '\uA7D5' to '\uA7D4',
        '\uA7D7' to '\uA7D6', '\uA7D9' to '\uA7D8', '\uA7DB' to '\uA7DA',
    )

    val canonical: CharArray = CharArray(CharSet.MAX_UNIT + 1) { canonicalize(it.toChar()) }

    private val orbits: List<CharArray> = (0..CharSet.MAX_UNIT).groupBy { canonical[it] }.values
        .filter { it.size > 1 }
        .map { members -> CharArray(members.size) { members[it].toChar() } }
        .sortedBy { it[0] }

    fun fold(set: CharSet): CharSet {
        val extra = IntList()
        for (orbit in orbits) {
            if (orbit.none { it in set }) continue
            for (unit in orbit) extra.addRange(unit.code, unit.code)
        }
        if (extra.isEmpty()) return set
        return set.union(CharSet.of(extra.toArray()))
    }

    private fun canonicalize(unit: Char): Char {
        if (unit.isSurrogate()) return unit
        newerUppercase[unit]?.let { return it }
        val upper = unit.toString().uppercase(Locale.ROOT)
        if (upper.length != 1 || (unit.code >= 128 && upper[0].code < 128)) return unit
        return upper[0]
    }
}
