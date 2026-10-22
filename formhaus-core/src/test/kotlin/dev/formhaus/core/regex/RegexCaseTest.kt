package dev.formhaus.core.regex

import dev.formhaus.core.support.Fixtures
import dev.formhaus.core.support.Load
import dev.formhaus.core.support.RawJson
import dev.formhaus.core.support.RawReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.readBytes

class RegexCaseTest {
    private fun describe(text: String): String =
        text
            .map {
                if (it.code in 0x20..0x7E) {
                    "$it"
                } else {
                    "\\u%04X".format(it.code)
                }
            }.joinToString("")

    @Test
    fun `every generated case matches the recorded node result`() {
        val file = requireNotNull(RawReader.parse(Fixtures.regex.resolve("cases.json").readBytes()))
        val cases = file["cases"]?.items.orEmpty()
        val counts = (file["counts"] as RawJson.Object).members.sumOf { requireNotNull(it.second.number).toInt() }
        assertEquals(counts, cases.size)
        val failures = ArrayList<String>()
        var fuzz = 0
        for (item in cases) {
            val kind = item["kind"]?.units
            if (kind == "fuzz") {
                fuzz++
                if (!Load.heavy && fuzz % 8 != 0) continue
            }
            val source = requireNotNull(item["pattern"]?.units)
            val input = requireNotNull(item["input"]?.units)
            val expected = (item["result"] as? RawJson.Bool)?.value?.toString() ?: "SyntaxError"
            val pattern = JsRegex.compile(source)
            val actual = if (pattern == null) "SyntaxError" else pattern.test(input).toString()
            if (actual != expected) failures.add("$kind ${describe(source)} on ${describe(input)}: $actual")
        }
        assertTrue(
            failures.isEmpty(),
            "${failures.size} of ${cases.size} cases differ from node ${file["node"]?.units}:\n${failures.take(
                40,
            ).joinToString("\n")}",
        )
    }
}
