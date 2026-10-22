package dev.formhaus.core.support

import dev.formhaus.core.definitionvalidation.checkDefinition
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class ConformanceFilesTest {
    private fun decodes(definition: JsonValue?) {
        val json = requireNotNull(definition?.objectValue) { "definition is not an object" }
        val form = FormDefinition(json)
        val nodes = json["steps"]?.arrayValue ?: json["fields"]?.arrayValue
        assertEquals(nodes?.count { it.objectValue != null } ?: 0, form.steps?.size ?: form.fields?.size ?: 0)
    }

    @Test
    fun `fixtures record the core ref they were synced from`() {
        val sha = Fixtures.version["sha"].orEmpty()
        val ref = Fixtures.version["ref"].orEmpty()
        assertTrue(Regex("[0-9a-f]{40}").matches(sha))
        assertTrue(ref.startsWith("@formhaus/core@") || ref == sha)
        assertTrue(Fixtures.conformanceNames.isNotEmpty())
        assertTrue(Fixtures.scenarioNames.isNotEmpty())
    }

    @TestFactory
    fun `definition file loads and decodes`(): List<DynamicTest> =
        Fixtures.conformanceNames.map { name ->
            DynamicTest.dynamicTest(name) {
                val file = Fixtures.conformanceFile(name)
                assertNotNull(file["name"]?.stringValue)
                decodes(file["definition"])
            }
        }

    @TestFactory
    fun `scenario file loads and decodes`(): List<DynamicTest> =
        Fixtures.scenarioNames.map { name ->
            DynamicTest.dynamicTest(name) {
                val file = Fixtures.scenarioFile(name)
                assertNotNull(file["name"]?.stringValue)
                val definition = file["definition"]
                decodes(definition)
                val scenarios = requireNotNull(file["scenarios"]?.arrayValue)
                assertFalse(scenarios.isEmpty())
                for (scenario in scenarios) {
                    assertNotNull(scenario["name"]?.stringValue)
                    if (scenario["throws"] != null) continue
                    val own = requireNotNull(scenario["definition"] ?: definition)
                    decodes(own)
                    assertTrue(checkDefinition(own).none { it.isError }, scenario["name"]?.stringValue)
                }
            }
        }

    @Test
    fun `lone surrogate fixtures load through the raw reader`() {
        val orders = Fixtures.orders("option-order")
        assertTrue(orders.isNotEmpty())
        assertTrue(orders.any { it["seed"]?.units == "\uD800" })
        assertTrue(Fixtures.conformanceFile("option-order")["orders"] == null)
    }
}
