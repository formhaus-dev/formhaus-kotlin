package dev.formhaus.research

import dev.formhaus.core.support.Fixtures
import dev.formhaus.core.support.field
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class OptionOrderTest {
    @TestFactory
    fun vectors(): List<DynamicTest> =
        Fixtures.conformanceNames.flatMap { name ->
            Fixtures.orders(name).mapIndexed { index, vector ->
                DynamicTest.dynamicTest("$name: $index") {
                    val pinned =
                        vector["pinned"]
                            ?.items
                            .orEmpty()
                            .mapNotNull { it.number?.toInt() }
                            .toSet()
                    val count = vector["count"]?.number?.toInt() ?: 0
                    val expected = vector["order"]?.items.orEmpty().mapNotNull { it.number?.toInt() }
                    val seed = requireNotNull(vector["seed"]?.units)
                    val key = requireNotNull(vector["key"]?.units)
                    assertEquals(expected, optionOrder(seed, key, (0 until count).map { it in pinned }))
                }
            }
        }

    @Test
    fun `the vectors keep the code units of a lone surrogate seed`() {
        assertTrue(Fixtures.orders("option-order").any { it["seed"]?.units == "\uD800" })
    }

    @Test
    fun `the research order keeps pinned and exclusive options in place`() {
        val options =
            (0 until 6)
                .joinToString(",") { index ->
                    val flag =
                        when (index) {
                            0 -> ""","pinned":true"""
                            5 -> ""","exclusive":true"""
                            else -> ""
                        }
                    """{"value":"o$index","label":"O"$flag}"""
                }.let { field("""{"key":"plan","type":"radio","label":"Plan","options":[$it]}""").options.orEmpty() }
        val order = Research().order("seed", "plan", options).mapNotNull { it.value }
        val pinned = listOf(true, false, false, false, false, true)
        assertEquals(optionOrder("seed", "plan", pinned).map { "o$it" }, order)
        assertEquals("o0", order.first())
        assertEquals("o5", order.last())
        assertEquals(32, Research().seed().length)
        assertNotEquals(Research().seed(), Research().seed())
    }
}
