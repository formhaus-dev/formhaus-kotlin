package dev.formhaus.core.corpus

import dev.formhaus.core.json.JsonValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

class RevealFuzzTest {
    @Test
    fun `matches revealTargets of @formhaus core on generated definitions`() {
        val path = System.getenv("FORMHAUS_REVEAL_FUZZ")
        assumeTrue(path != null, "runs with scripts/reveal-fuzz.sh")
        var cases = 0
        var mismatches = 0
        val samples = mutableListOf<String>()
        File(path).forEachLine { line ->
            if (line.isEmpty()) return@forEachLine
            val entry = JsonValue.parse(line)
            cases++
            if (revealPointers(entry["definition"] ?: JsonValue.Null) != entry["reveals"]) {
                mismatches++
                if (samples.size < 5) samples += line
            }
        }
        println("reveal fuzz: $cases definitions, $mismatches mismatches")
        assertTrue(cases > 0)
        assertEquals(0, mismatches, samples.joinToString("\n"))
    }
}
