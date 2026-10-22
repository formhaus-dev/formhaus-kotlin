package dev.formhaus.core.visibility

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.support.definition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ListLimitsTest {
    private fun options(count: Int) = (0 until count).joinToString(",") { """{"value":"o$it","label":"o$it"}""" }

    private fun picks(count: Int): JsonValue = JsonValue.Array((0 until count).map { JsonValue.String("o$it") })

    @Test
    fun `contains stops at 1000 items unless the field owns more static options`() {
        val form = definition(
            """{"id":"f","submit":{"label":"S","disabled":[{"field":"big","contains":"o5"}]},"fields":[
              {"key":"big","type":"multiselect","label":"B","options":[${options(1500)}]},
              {"key":"from","type":"multiselect","label":"F","optionsFrom":"api","options":[${options(1500)}]}]}""",
        )
        val condition = requireNotNull(form.submit.disabled).first()
        val values = stringMapOf("big" to picks(1200))
        assertFalse(evaluateCondition(condition, values))
        val limits = ListLimits.STANDARD.trimmed(form.allFields)
        assertEquals(1500, limits.length("big"))
        assertNull(limits.length("from"))
        assertTrue(evaluateCondition(condition, values, limits))
        assertFalse(evaluateCondition(condition, stringMapOf("big" to picks(1600)), limits))
        assertTrue(evaluateCondition(condition, stringMapOf("big" to picks(1000))))
        assertNull(ListLimits.STANDARD.length("big"))
    }

    @Test
    fun `item paths split only on a canonical index`() {
        assertEquals(2 to "name", splitPath("people", "people.2.name"))
        assertEquals(0 to "a.b", splitPath("people", "people.0.a.b"))
        assertNull(splitPath("people", "people.02.name"))
        assertNull(splitPath("people", "people.x.name"))
        assertNull(splitPath("people", "people.1."))
        assertNull(splitPath("people", "persons.1.name"))
        assertNull(splitPath("people", "people.99999999999.name"))
    }
}
