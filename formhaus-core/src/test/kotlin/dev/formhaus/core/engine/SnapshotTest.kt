package dev.formhaus.core.engine

import dev.formhaus.core.definitionvalidation.DefinitionIssue
import dev.formhaus.core.definitionvalidation.IssueCode
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.support.jsonObject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SnapshotTest {
    private val trip =
        """{"id":"trip","title":"Trip","submit":{"label":"Book"},"steps":[
        {"id":"who","title":"Who","fields":[{"key":"name","type":"text","label":"Name","validation":{"required":true}}]},
        {"id":"extras","title":"Extras","skip":{"label":"Skip"},"fields":[{"key":"meal","type":"text","label":"Meal","defaultValue":"veg"}]},
        {"id":"seat","title":"Seat","fields":[{"key":"seat","type":"radio","label":"Seat","validation":{"required":true},
          "options":[{"value":"a","label":"Aisle"},{"value":"w","label":"Window"}]}]}]}"""

    private val example =
        """{"version":1,"definitionId":"trip","values":{"name":"Ada"},"step":"seat","skipped":["extras"],"seed":"abc"}"""

    private val issues = mutableListOf<DefinitionIssue>()
    private val options = FormEngineOptions(onWarning = { issues += it })

    private fun restored(text: String): FormEngine =
        FormEngine(JsonValue.parse(trip), options = options, restoring = FormSnapshot.fromJson(text))

    @Test
    fun `a snapshot restores values, step, skipped steps and seed without validating`() {
        val engine = restored(example)
        assertEquals("seat", engine.currentStep?.id)
        assertEquals("abc", engine.seed)
        assertEquals(jsonObject("""{"name":"Ada"}"""), engine.values)
        assertTrue(engine.errors.isEmpty())
        assertEquals(listOf("extras"), engine.snapshot().skipped)
        assertTrue(issues.none { it.code == IssueCode.SNAPSHOT_MISMATCH })
        val sent = mutableListOf<List<String>>()
        runBlocking {
            engine.setValue("seat", JsonValue.String("w"))
            engine.submit { _, skipped -> SubmitResult.Ok.also { sent += skipped } }
        }
        assertEquals(listOf(listOf("extras")), sent)
    }

    @Test
    fun `a snapshot round-trips through json`() {
        val engine = restored(example)
        val snapshot = engine.snapshot()
        assertEquals(snapshot, FormSnapshot.fromJson(snapshot.toJson()))
        assertEquals(FormSnapshot.fromJson(example), snapshot)
    }

    @Test
    fun `a mismatched snapshot starts fresh with a warning`() {
        val cases =
            listOf(
                example.replace("\"version\":1", "\"version\":2"),
                example.replace("\"definitionId\":\"trip\"", "\"definitionId\":\"other\""),
                example.replace("\"step\":\"seat\"", "\"step\":\"gone\""),
                example.replace("[\"extras\"]", "[\"gone\"]"),
            )
        for (case in cases) {
            issues.clear()
            val engine = restored(case)
            assertEquals("who", engine.currentStep?.id)
            assertNull(engine.values["name"])
            assertEquals(listOf(IssueCode.SNAPSHOT_MISMATCH), issues.map { it.code })
        }
    }

    @Test
    fun `a snapshot without a definition id checks only the version`() {
        val engine = restored(example.replace("\"definitionId\":\"trip\"", "\"definitionId\":null"))
        assertEquals("seat", engine.currentStep?.id)
        assertEquals("trip", engine.snapshot().definitionId)
    }

    @Test
    fun `malformed json reads as no snapshot`() {
        for (text in listOf("", "[]", "{", """{"version":"1","values":{},"skipped":[],"seed":""}""")) {
            assertNull(FormSnapshot.fromJson(text))
        }
    }
}
