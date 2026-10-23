package dev.formhaus.core.renderer

import dev.formhaus.core.engine.errorsOf
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.support.engine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ErrorSummaryTest {
    private val trip =
        """{"id":"trip","title":"","submit":{"label":"Send"},"steps":[
        {"id":"who","title":"Who","fields":[
          {"key":"name","type":"text","label":"Name","validation":{"required":true}},
          {"key":"guests","type":"group","label":"Guests","defaultItems":2,"fields":[{"key":"guest","type":"text","label":"Guest"}]}]},
        {"id":"where","fields":[{"key":"city","type":"text","label":"City"}]}]}"""

    private fun ErrorSummary.rows(): List<List<String>> =
        groups.map { group -> listOf(group.id, group.title) + group.entries.flatMap { listOf(it.key, it.id, it.text) } }

    @Test
    fun `is empty for a form without errors`() {
        val summary = engine(trip).errorSummary()
        assertEquals(ErrorSummary(emptyList(), emptyList(), 0), summary)
    }

    @Test
    fun `groups field errors by step with item paths, ids and counts`() {
        val engine = engine(trip)
        engine.setErrors(errorsOf("""{"name":"Taken","guests.1.guest":"Too short","city":"Unknown","coupon":"Server down"}"""))
        val summary = engine.errorSummary(ErrorSummaryOptions(idPrefix = "trip"))
        assertEquals(
            listOf(
                listOf(
                    "who",
                    "Who",
                    "name",
                    "trip-name",
                    "Name: Taken",
                    "guests.1.guest",
                    "trip-guests--1--guest",
                    "Guest (Guests 2): Too short",
                ),
                listOf("where", "Step 2", "city", "trip-city", "City: Unknown"),
            ),
            summary.rows(),
        )
        assertEquals(listOf("Server down"), summary.topLevelErrors)
        assertEquals(3, summary.count)
    }

    @Test
    fun `uses the default id prefix`() {
        val engine = engine(trip)
        engine.setErrors(errorsOf("""{"name":"Taken"}"""))
        assertEquals(
            "fh-name",
            engine
                .errorSummary()
                .groups[0]
                .entries[0]
                .id,
        )
    }

    @Test
    fun `reads labels from the options before the definition`() {
        val engine =
            engine(
                trip.replaceFirst(
                    "\"title\":\"\",",
                    "\"title\":\"\",\"labels\":{\"stepTitle\":\"Part {index}\",\"itemLabel\":\"Guest {index}\"},",
                ),
            )
        engine.setErrors(errorsOf("""{"guests.0.guest":"Missing","city":"Unknown"}"""))
        val labels = LabelSource.Catalog(JsonValue.parse("""{"itemLabel":"Person {index}"}"""))
        val groups = engine.errorSummary(ErrorSummaryOptions(labels = labels)).groups
        assertEquals(
            listOf("Who" to listOf("Guest (Person 1): Missing"), "Part 2" to listOf("City: Unknown")),
            groups.map { group -> group.title to group.entries.map { it.text } },
        )
    }
}
