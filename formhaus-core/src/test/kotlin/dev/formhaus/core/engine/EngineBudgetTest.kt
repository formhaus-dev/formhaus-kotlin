package dev.formhaus.core.engine

import dev.formhaus.core.regex.CallBudget
import dev.formhaus.core.support.engine
import dev.formhaus.core.support.text
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EngineBudgetTest {
    private fun patterned(
        count: Int,
        match: String? = null,
    ): List<String> =
        (0 until count).map { index ->
            val rules = if (match == null) """{"pattern":"^a$"}""" else """{"pattern":"^a$","matchField":"$match"}"""
            """{"key":"f$index","type":"text","label":"F","validation":$rules}"""
        }

    private fun filled(
        count: Int,
        extra: String = "",
    ): String =
        (0 until count).joinToString(",", "{", "$extra}") {
            "\"f$it\":\"a\""
        }

    private fun steps(count: Int): String =
        """{"id":"p","title":"P","submit":{"label":"S"},"steps":[
        {"id":"one","title":"1","fields":[${patterned(count).joinToString(",")}]},
        {"id":"two","title":"2","fields":[{"key":"z","type":"text","label":"Z"}]}]}"""

    private fun flat(fields: List<String>): String =
        """{"id":"p","title":"P","submit":{"label":"S"},"fields":[${fields.joinToString(",")}]}"""

    @Test
    fun canGoNext() {
        val open = engine(steps(5), filled(5))
        assertTrue(CallBudget.patternTestLimit.withValue(5) { open.canGoNext })
        val capped = engine(steps(5), filled(5))
        assertFalse(CallBudget.patternTestLimit.withValue(3) { capped.canGoNext })
    }

    @Test
    fun validate() {
        val engine = engine(flat(patterned(5)), filled(5))
        assertEquals(
            listOf("f3", "f4"),
            CallBudget.patternTestLimit
                .withValue(3) { engine.validate() }
                .keys
                .sorted(),
        )
    }

    @Test
    fun `setValue revalidates match errors`() {
        val fields = patterned(5, "src") + """{"key":"src","type":"text","label":"S"}"""
        val engine = engine(flat(fields), filled(5, ",\"src\":\"b\""))
        assertEquals(5, engine.validate().size)
        CallBudget.patternTestLimit.withValue(3) { engine.setValue("src", text("a")) }
        assertEquals(listOf("f3", "f4"), engine.errors.keys.sorted())
    }

    @Test
    fun `setItemValue revalidates match errors`() {
        val inner = patterned(5, "src") + """{"key":"src","type":"text","label":"S"}"""
        val group = """{"key":"g","type":"group","label":"G","fields":[${inner.joinToString(",")}]}"""
        val engine = engine(flat(listOf(group)), """{"g":[${filled(5, ",\"src\":\"b\"")}]}""")
        assertEquals(5, engine.validate().size)
        CallBudget.patternTestLimit.withValue(3) { engine.setItemValue("g", 0, "src", text("a")) }
        assertEquals(listOf("g.0.f3", "g.0.f4"), engine.errors.keys.sorted())
    }

    @Test
    fun next() {
        val open = engine(steps(3), filled(3))
        assertTrue(CallBudget.patternTestLimit.withValue(6) { runBlocking { open.next() } })
        val capped = engine(steps(3), filled(3))
        assertFalse(CallBudget.patternTestLimit.withValue(4) { runBlocking { capped.next() } })
    }

    @Test
    fun submit() {
        val options = FormEngineOptions(onWarning = {}, onBeforeSubmit = { true })
        val open = engine(flat(patterned(3)), filled(3), options)
        assertTrue(CallBudget.patternTestLimit.withValue(6) { runBlocking { open.submit { _, _ -> SubmitResult.Ok } } })
        val capped = engine(flat(patterned(3)), filled(3), options)
        assertFalse(
            CallBudget.patternTestLimit.withValue(4) {
                runBlocking {
                    capped.submit {
                        _,
                        _,
                        ->
                        SubmitResult.Ok
                    }
                }
            },
        )
    }
}
