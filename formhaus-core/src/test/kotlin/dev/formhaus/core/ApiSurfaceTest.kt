package dev.formhaus.core

import dev.formhaus.core.definitionvalidation.IssueCode
import dev.formhaus.core.definitionvalidation.checkDefinition
import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.i18n.localize
import dev.formhaus.core.i18n.textSlots
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.server.SubmissionOptions
import dev.formhaus.core.server.validateSubmission
import dev.formhaus.core.support.jsonObject
import dev.formhaus.core.validation.MessageKey
import dev.formhaus.core.validation.MessageSource
import dev.formhaus.core.visibility.RevealTarget
import dev.formhaus.core.visibility.revealTargets
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class ApiSurfaceTest {
    private val definition =
        JsonValue.parse(
            """{"id":"f","title":"F","submit":{"label":"S"},"steps":[
            {"id":"one","title":"One","fields":[{"key":"a","type":"text","label":"A","validation":{"required":true}}],
             "routes":[{"to":null,"show":[{"field":"a","eq":"end"}]}]},
            {"id":"two","title":"Two","show":[{"field":"a","neq":"skip"}],
             "fields":[{"key":"s","type":"section","title":"S","showAny":[{"field":"a","eq":"x"}],"fields":[]}]}]}""",
        )

    @Test
    fun `issue codes are an open string set`() {
        assertEquals("invalid-type", IssueCode.INVALID_TYPE.rawValue)
        assertEquals("future-code", IssueCode("future-code").rawValue)
        val issues = checkDefinition(JsonValue.parse("""{"id":"f","submit":{"label":"S"},"fields":1}"""))
        assertTrue(issues.any { it.code == IssueCode.INVALID_TYPE })
    }

    @Test
    fun `revealTargets maps each node of a list to the option that reveals it`() {
        val form =
            FormDefinition(
                JsonValue.parse(
                    """{"id":"f","submit":{"label":"S"},"fields":[
                    {"key":"a","type":"radio","label":"A","options":[{"value":"x","label":"X"},{"value":"y","label":"Y"}]},
                    {"key":"b","type":"text","label":"B","show":[{"field":"a","eq":"y"}]},
                    {"key":"c","type":"text","label":"C"}]}""",
                ),
            )
        assertEquals(listOf(null, RevealTarget(0, 1), null), revealTargets(form.fields.orEmpty()))
    }

    @Test
    fun `message functions receive message keys`() {
        val options =
            SubmissionOptions(
                messages =
                    MessageSource.Function { key, _, field ->
                        if (key == MessageKey.REQUIRED) {
                            "Need ${field.key}"
                        } else {
                            null
                        }
                    },
            )
        val result = validateSubmission(definition, JsonValue.Object(MutableJsonObject()), options)
        assertEquals(mapOf("a" to "Need a"), result.errors.toMap())
    }

    @Test
    fun `checks, submissions and slots run from any thread`() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results =
                pool
                    .invokeAll(
                        List(16) {
                            Callable {
                                val issues = checkDefinition(definition).count { it.isError }
                                val errors =
                                    validateSubmission(
                                        definition,
                                        JsonValue.parse("""{"a":"x"}"""),
                                    ).errors.size
                                val slots = textSlots(FormDefinition(definition)).size
                                val localized =
                                    localize(definition, JsonValue.parse("""{"title":"T"}""")).definition.title == "T"
                                issues + errors + (if (slots > 0) 0 else 100) + (if (localized) 0 else 1000)
                            }
                        },
                    ).map { it.get() }
            assertEquals(List(16) { 0 }, results)
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `the engine takes a JSON definition`() {
        val engine = FormEngine(definition, jsonObject("""{"a":"x"}"""))
        assertTrue(engine.isMultiStep)
        assertEquals("one", engine.currentStep?.id)
        assertTrue(engine.validate().isEmpty())
        assertEquals("1.6", FormDefinition.SUPPORTED_FORMAT_VERSION)
    }

    @Test
    fun `subscribers hear every change until they cancel`() {
        val engine = FormEngine(definition)
        var count = 0
        val cancel = engine.subscribe { count += 1 }
        engine.setValue("a", JsonValue.String("x"))
        val heard = count
        assertTrue(heard > 0)
        cancel()
        engine.setValue("a", JsonValue.String("y"))
        assertEquals(heard, count)
        assertTrue(runBlocking { engine.next() })
        assertEquals("two", engine.currentStep?.id)
    }
}
