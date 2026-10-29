package dev.formhaus.core.engine

import dev.formhaus.core.definitionvalidation.FormDefinitionError
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.mutableStringMapOf
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.renderer.RendererApi
import dev.formhaus.core.renderer.fileListLimit
import dev.formhaus.core.renderer.maxItemsMessage
import dev.formhaus.core.server.validateSubmission
import dev.formhaus.core.support.definition
import dev.formhaus.core.validation.validateFieldValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `the limit is maxLength floored and clamped to 0 to 1000 everywhere`() {
        val cases = listOf(1e300 to 1000, -1e300 to 0, -5.0 to 0, 2.5 to 2, 0.0 to 0, 1000.9 to 1000)
        for ((max, expected) in cases) {
            val engine = docs("maxLength" to max, "minLength" to minOf(max, -max))
            val message = "At most $expected allowed"
            assertEquals(expected, fileListLimit(engine.files), "maxLength $max")
            assertEquals(message, engine.maxItemsMessage(engine.files))
            val over = references(expected + 1)
            engine.setValue("d", over)
            assertEquals(message, engine.validate()["d"], "maxLength $max")
            val result = validateSubmission(JsonValue.Object(engine.definition.json), JsonValue.Object(stringMapOf("d" to over)))
            assertEquals(message, result.errors["d"], "maxLength $max")
            engine.setValue("d", references(expected))
            assertNull(engine.validate()["d"], "maxLength $max")
        }
    }

    @Test
    fun `a non-finite maxLength set through code counts as no maxLength`() {
        for (max in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val definition =
                definition(
                    """{"formatVersion":"1.7","id":"f","submit":{"label":"S"},"fields":[
                    {"key":"d","type":"file","label":"D","multiple":true,"validation":{}}]}""",
                )
            val field = definition.allFields.single()
            val json = field.json.toMutableStringMap().also { it["validation"] = obj("maxLength" to JsonValue.Number(max)) }
            val hostile = field.withJson(json)
            assertEquals(100, fileListLimit(hostile), "maxLength $max")
            assertEquals("At most 100 allowed", validateFieldValue(hostile, references(101), stringMapOf()))
        }
    }
}
