package dev.formhaus.core.server

import dev.formhaus.core.engine.HiddenValues
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.scenario.json
import dev.formhaus.core.support.Fixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class SubmissionFixturesTest {
    @TestFactory
    fun submissions(): List<DynamicTest> {
        assertTrue(Fixtures.submissionNames.isNotEmpty())
        return Fixtures.submissionNames.flatMap { name ->
            val file = Fixtures.submissionFile(name)
            val definition = file["definition"] ?: JsonValue.Null
            file["cases"]?.arrayValue.orEmpty().map { case ->
                DynamicTest.dynamicTest("$name: ${case["name"]?.stringValue.orEmpty()}") {
                    val input = case["input"] ?: JsonValue.Null
                    val options =
                        SubmissionOptions(
                            input["skippedSteps"],
                            hiddenValues = HiddenValues.of(input["hiddenValues"]?.stringValue) ?: HiddenValues.CLEAR,
                            previousValues = input["previousValues"]?.objectValue,
                        )
                    val result = validateSubmission(definition, input["values"] ?: JsonValue.Object(MutableJsonObject()), options)
                    assertEquals(case["expected"]?.get("values"), JsonValue.Object(result.values))
                    assertEquals(case["expected"]?.get("errors"), json(result.errors))
                }
            }
        }
    }
}
