package dev.formhaus.core.renderer

import dev.formhaus.core.support.definition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class DomIdsTest {
    private class Ids(
        private val formId: String?,
        private val ids: DomIds,
    ) {
        fun field(key: String) = ids.field(formId, key)

        fun item(
            group: String,
            index: Int,
            field: String,
        ) = ids.item(formId, group, index, field)
    }

    private fun text(key: String) = """{"key":"$key","type":"text","label":"$key"}"""

    private fun group(
        key: String,
        children: List<String>,
    ) = """{"key":"$key","type":"group","label":"$key","fields":[${children.joinToString(",", transform = ::text)}]}"""

    private fun ids(
        nodes: List<String>,
        formId: String? = "form",
    ) = Ids(formId, definition("""{"id":"f","submit":{"label":"S"},"fields":[${nodes.joinToString(",")}]}""").domIds)

    @Test
    fun `keeps the id of keys that collide with no other key`() {
        val keys = listOf("email", "first_name", "contact.email", "Ünit", "a-b")
        val ids = ids(keys.map(::text))
        assertEquals(listOf("form-email", "form-first_name", "form-contact-email", "form--nit", "form-a-b"), keys.map(ids::field))
        assertEquals("fh-contact-email", ids(emptyList(), null).field("contact.email"))
    }

    @Test
    fun `gives the escaped key a suffix derived from the key when it collides`() {
        val keys = listOf("contact.email", "contact-email", "contact:email")
        val expected = listOf("form-contact-email-l7cb8l", "form-contact-email", "form-contact-email-19nbjqx")
        assertEquals(expected, keys.map(ids(keys.map(::text))::field))
        assertEquals(expected, keys.map(ids(keys.reversed().map(::text))::field))
    }

    @Test
    fun `gives sections and item fields ids that collide with no other node`() {
        val nodes =
            listOf(
                """{"key":"contact.email","type":"section","title":"Contact","fields":[${text("phone")}]}""",
                text("contact-email"),
                group("crew", listOf("contact.email", "contact-email", "a.b")),
            )
        val ids = ids(nodes)
        assertEquals("form-contact-email", ids.field("contact-email"))
        assertNotEquals("form-contact-email", ids.field("contact.email"))
        val items = listOf("contact-email", "contact.email", "a.b").map { ids.item("crew", 1, it) }
        assertEquals("form-crew--1--contact-email", items[0])
        assertEquals("form-crew--1--a-b", items[2])
        assertEquals(3, items.toSet().size)
    }

    @Test
    fun `keeps item ids apart from keys that look like item ids`() {
        val shadowed = ids(listOf(text("crew--0--contact-email"), group("crew", listOf("contact-email"))), "f")
        assertEquals("f-crew--0--contact-email", shadowed.field("crew--0--contact-email"))
        assertEquals("f-crew-14v7hiw--0--contact-email", shadowed.item("crew", 0, "contact-email"))
        val meeting = ids(listOf(group("crew--0", listOf("contact-email")), group("crew", listOf("0--contact-email"))), "f")
        assertEquals("f-crew--0--0--contact-email", meeting.item("crew--0", 0, "contact-email"))
        assertEquals("f-crew-14v7hiw--0--0--contact-email", meeting.item("crew", 0, "0--contact-email"))
        val plain = ids(listOf(text("crew-0"), group("crew", listOf("name")), group("crews", listOf("name"))), "f")
        assertEquals("f-crew--0--name", plain.item("crew", 0, "name"))
        assertEquals("f-crews--0--name", plain.item("crews", 0, "name"))
    }

    @Test
    fun `assigns the ids once per definition`() {
        val definition = definition("""{"id":"f","submit":{"label":"S"},"fields":[${text("a")}]}""")
        assertSame(definition.domIds, definition.domIds)
    }
}
