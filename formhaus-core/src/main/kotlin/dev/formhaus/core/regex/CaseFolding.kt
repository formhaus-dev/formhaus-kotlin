package dev.formhaus.core.regex

import dev.formhaus.core.unicode.canonicalRuns
import java.util.TreeSet

internal object CaseFolding {
    private const val LATIN = 256

    private val latin = CharArray(LATIN) { canonicalRuns.map(it).toChar() }

    private val orbits: List<CharArray> =
        HashMap<Int, TreeSet<Int>>()
            .also { groups ->
                canonicalRuns.forEachMapped { unit, target -> groups.getOrPut(target, ::TreeSet).add(unit) }
                for ((target, members) in groups) if (canonicalRuns.map(target) == target) members.add(target)
            }.values
            .filter { it.size > 1 }
            .map { members ->
                CharArray(members.size).also { units ->
                    members.forEachIndexed { index, unit -> units[index] = unit.toChar() }
                }
            }.sortedBy { it[0] }

    fun canonical(code: Int): Char = if (code < LATIN) latin[code] else canonicalRuns.map(code).toChar()

    fun fold(set: CharSet): CharSet {
        val extra = IntList()
        for (orbit in orbits) {
            if (orbit.none { it in set }) continue
            for (unit in orbit) extra.addRange(unit.code, unit.code)
        }
        if (extra.isEmpty()) return set
        return set.union(CharSet.of(extra.toArray()))
    }
}
