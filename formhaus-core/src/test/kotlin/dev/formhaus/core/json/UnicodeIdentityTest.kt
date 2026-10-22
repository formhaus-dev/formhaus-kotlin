package dev.formhaus.core.json

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class UnicodeIdentityTest {
    private val composed = "caf\u00E9"
    private val decomposed = "cafe\u0301"

    @Test
    fun `canonically equivalent strings stay distinct`() {
        assertNotEquals(JsonValue.String(composed), JsonValue.String(decomposed))
        val members = stringMapOf(composed to JsonValue.Number(1), decomposed to JsonValue.Number(2))
        assertEquals(2, members.size)
        assertEquals(JsonValue.Number(2), members[decomposed])
        assertNotEquals(JsonValue.String("K"), JsonValue.String("\u212A"))
    }

    @Test
    fun `parsed keys compare by code units`() {
        val value = JsonValue.parse("{\"$composed\":1,\"$decomposed\":2}")
        assertEquals(listOf(composed, decomposed), value.objectValue?.keys)
    }
}
