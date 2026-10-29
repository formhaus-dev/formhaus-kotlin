package dev.formhaus.core.engine

import dev.formhaus.core.definitionvalidation.FormDefinitionError
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.mutableStringMapOf
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.renderer.RendererApi
import dev.formhaus.core.renderer.itemLimits
import dev.formhaus.core.renderer.maxItemsMessage
import dev.formhaus.core.server.validateSubmission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@OptIn(RendererApi::class)
class FileListLimitTest {
    private fun obj(vararg pairs: Pair<String, JsonValue>): JsonValue = JsonValue.Object(mutableStringMapOf(*pairs))

    private fun docs(vararg rules: Pair<String, Double>): FormEngine {
        val validation = obj(*rules.map { (name, value) -> name to JsonValue.Number(value) }.toTypedArray())
        val field =
            obj(
                "key" to JsonValue.String("d"),
                "type" to JsonValue.String("file"),
                "label" to JsonValue.String("D"),
                "multiple" to JsonValue.Bool(true),
                "validation" to validation,
            )
        val definition =
            obj(
                "formatVersion" to JsonValue.String("1.7"),
                "id" to JsonValue.String("f"),
                "submit" to obj("label" to JsonValue.String("S")),
                "fields" to JsonValue.Array(listOf(field)),
            )
        return FormEngine(FormDefinition(definition), options = FormEngineOptions(onWarning = {}))
    }

    private val FormEngine.files: FormField get() = definition.allFields.single()

    private fun references(count: Int) = JsonValue.Array(List(count) { JsonValue.String("upload-$it") })

    @Test
    fun `non-finite counts are rejected as definition errors`() {
        for (max in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(FormDefinitionError::class.java) { docs("maxLength" to max) }
            assertThrows(FormDefinitionError::class.java) { docs("minLength" to max) }
        }
    }

    @Test
    fun `hostile counts clamp the picker limit and never throw`() {
        val cases = listOf(1e300 to 1000, -1e300 to 0, -3.0 to 0, 2.5 to 2, 0.0 to 0)
        for ((max, expected) in cases) {
            val engine = docs("maxLength" to max, "minLength" to minOf(max, -max))
            assertEquals(expected, engine.itemLimits(engine.files).max, "maxLength $max")
            engine.maxItemsMessage(engine.files)
            engine.setValue("d", references(3))
            engine.validate()
            validateSubmission(JsonValue.Object(engine.definition.json), JsonValue.Object(stringMapOf("d" to references(3))))
        }
    }

    @Test
    fun `the cap holds for huge maxLength and negative limits fail every list`() {
        val huge = docs("maxLength" to 1e300)
        huge.setValue("d", references(1001))
        assertEquals("At most 1000 allowed", huge.validate()["d"])
        assertEquals("At most 1000 allowed", huge.maxItemsMessage(huge.files))
        val negative = docs("maxLength" to -3.0, "minLength" to -5.0)
        negative.setValue("d", references(1))
        assertEquals("At most -3 allowed", negative.validate()["d"])
    }
}
