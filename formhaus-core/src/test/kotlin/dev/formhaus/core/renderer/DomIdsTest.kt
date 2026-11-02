package dev.formhaus.core.renderer

import dev.formhaus.core.support.definition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
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

    private fun node(
        key: String,
        type: String,
        extra: String = "",
    ) = """{"key":"$key","type":"$type","label":"$key"$extra}"""

    private fun form(
        version: String,
        nodes: List<String>,
    ) = Ids(
        "f",
        definition("""{"id":"f","formatVersion":"$version","submit":{"label":"S"},"fields":[${nodes.joinToString(",")}]}""").domIds,
    )

    @Test
    fun `keeps the ids of a form whose keys only look alike`() {
        val nodes =
            listOf(
                node("address", "text"),
                node("address-2", "text"),
                node("name", "text"),
                node("name-error-free", "text"),
                node("company", "text"),
                node("company-description", "text"),
                node("size", "radio", ""","options":[{"value":"s","label":"S"}]"""),
                node("size-2", "text"),
                node("size-0-description", "text"),
                node("nps", "scale", ""","validation":{"min":0,"max":3}"""),
                node("nps-9", "text"),
                node("nps-anchor-0", "text"),
                node("crew", "group", ""","fields":[${node("name", "text")}]"""),
                node("crew-label", "text"),
                node("crew-helper", "text"),
            )
        val keys = nodes.map { Regex("\"key\":\"([^\"]+)\"").find(it)!!.groupValues[1] }
        val ids = form("1.1", nodes)
        assertEquals(keys.map { "f-$it" }, keys.map(ids::field))
    }

    @Test
    fun `moves a field id off the generated ids of another node`() {
        val described = ""","options":[{"value":"a","label":"A","description":"First"},{"value":"b","label":"B","description":"Second"}]"""
        val keys =
            listOf(
                "plan",
                "plan-helper",
                "plan-1",
                "plan-0-description",
                "plan-description",
                "nps",
                "nps-anchor",
                "nps-3",
                "city",
                "city-list",
                "city-label",
                "docs",
                "docs-notice",
                "crew",
                "crew-confirm",
                "crew-error",
            )
        val nodes =
            listOf(
                node("plan", "radio", "$described,\"helperText\":\"Pick\""),
                node("plan-helper", "text"),
                node("plan-1", "text"),
                node("plan-0-description", "text"),
                node("plan-description", "text"),
                node("nps", "scale", ""","validation":{"min":0,"max":3},"options":[{"value":"0","label":"Low"}]"""),
                node("nps-anchor", "radio", ""","options":[{"value":"x","label":"X"}]"""),
                node("nps-3", "text"),
                node("city", "autocomplete", described),
                node("city-list", "text"),
                node("city-label", "text"),
                node("docs", "file", ""","multiple":true"""),
                node("docs-notice", "text"),
                node("crew", "group", ""","fields":[${node("name", "text")},${node("name-error", "text")}]"""),
                node("crew-confirm", "text"),
                node("crew-error", "text"),
            )
        val ids = form("1.7", nodes)
        val assigned = keys.map(ids::field)
        assertEquals(
            listOf("f-plan", "f-nps", "f-city", "f-docs", "f-crew-confirm", "f-crew-error"),
            assigned.filterIndexed { index, id -> id == "f-${keys[index]}" },
        )
        assigned.forEachIndexed { index, id -> assertTrue(Regex("^f-${keys[index]}(-[0-9a-z]+)?$").matches(id), id) }
        assertTrue(Regex("^f-crew-[0-9a-z]+--0--name$").matches(ids.item("crew", 0, "name")))
        assertTrue(Regex("^f-crew-[0-9a-z]+--0--name-error-[0-9a-z]+$").matches(ids.item("crew", 0, "name-error")))
    }

    @Test
    fun `collapses the dashes before reveal`() {
        assertEquals("fh-a-reveal", ids(emptyList(), null).field("a--reveal"))
    }

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
