package dev.formhaus.core.renderer

import dev.formhaus.core.definitionvalidation.FormDefinitionError
import dev.formhaus.core.engine.SubmitResult
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.parseDefinition
import dev.formhaus.core.support.engine
import dev.formhaus.core.support.field
import dev.formhaus.core.support.text
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(RendererApi::class)
class RendererStateTest {
    private val form =
        """{"id":"f","submit":{"label":"S"},"fields":[
        {"key":"name","type":"text","label":"Name","validation":{"required":true}},
        {"key":"email","type":"email","label":"Email","validation":{"required":true}},
        {"key":"team","type":"group","label":"Team","fields":[
          {"key":"who","type":"text","label":"Who","validation":{"required":true}},
          {"key":"vegan","type":"checkbox","label":"Vegan"},
          {"key":"size","type":"text","label":"Size","defaultValue":"M"}]}]}"""

    @Test
    fun `errorsVersion moves only when the errors are replaced`() {
        val engine = engine(form)
        val start = engine.errorsVersion
        engine.setValue("name", text("Ada"))
        assertEquals(start, engine.errorsVersion)
        engine.validate()
        val validated = engine.errorsVersion
        assertNotEquals(start, validated)
        engine.setErrors(stringMapOf("name" to "Taken"))
        assertNotEquals(validated, engine.errorsVersion)
    }

    @Test
    fun `definitionRevision moves when the definition is replaced`() {
        val engine = engine(form)
        val start = engine.definitionRevision
        engine.setValue("name", text("Ada"))
        assertEquals(start, engine.definitionRevision)
        engine.replaceDefinition(JsonValue.parse(form))
        assertEquals(start + 1, engine.definitionRevision)
    }

    @Test
    fun `restarts moves on reset and on a replaced definition only`() {
        val engine = engine(form)
        val start = engine.restarts
        engine.setValue("name", text("Ada"))
        engine.validate()
        assertEquals(start, engine.restarts)
        engine.reset()
        assertEquals(start + 1, engine.restarts)
        engine.replaceDefinition(JsonValue.parse(form))
        assertEquals(start + 2, engine.restarts)
    }

    @Test
    fun `sending is true only while the submit handler runs`() {
        val engine = engine(form, """{"name":"Ada","email":"ada@example.com"}""")
        val seen = mutableListOf<Boolean>()
        assertFalse(engine.sending)
        runBlocking {
            engine.submit { _, _ ->
                seen += engine.sending
                SubmitResult.Ok
            }
        }
        assertEquals(listOf(true), seen)
        assertFalse(engine.sending)
    }

    @Test
    fun `firstErrorKey follows the field order, item paths included`() {
        val engine = engine(form, """{"name":"Ada","team":[{"who":"Bo"},{}]}""")
        assertNull(engine.firstErrorKey())
        engine.validate()
        assertEquals("email", engine.firstErrorKey())
        engine.setValue("email", text("ada@example.com"))
        engine.validate()
        assertEquals("team.1.who", engine.firstErrorKey())
    }

    @Test
    fun `firstErrorKey looks at the current step only`() {
        val engine =
            engine(
                """{"id":"f","submit":{"label":"S"},"steps":[
                {"id":"one","fields":[{"key":"a","type":"text","label":"A"}]},
                {"id":"two","fields":[{"key":"b","type":"text","label":"B"}]}]}""",
            )
        engine.setErrors(stringMapOf("b" to "Wrong"))
        assertEquals("two", engine.currentStep?.id)
        assertEquals("b", engine.firstErrorKey())
        engine.goToStep("one")
        assertNull(engine.firstErrorKey())
    }

    @Test
    fun `hasValue treats empty text and missing values as empty and defaults as values`() {
        val engine = engine(form, """{"name":"","email":"a@b.c","team":[{"who":"Bo","vegan":false},{}]}""")
        assertFalse(engine.hasValue("name"))
        assertTrue(engine.hasValue("email"))
        assertTrue(engine.hasValue("team.0.who"))
        assertTrue(engine.hasValue("team.0.vegan"))
        assertTrue(engine.hasValue("team.0.size"))
        assertFalse(engine.hasValue("team.1.who"))
        assertFalse(engine.hasValue("team.5.who"))
        assertFalse(engine.hasValue("missing"))
    }

    @Test
    fun `item content ignores blanks, false and default values`() {
        val engine =
            engine(
                form,
                """{"team":[{"who":"  ","vegan":false,"size":"M"},{"who":"Bo"},{"vegan":true},{"size":"L"},7]}""",
            )
        assertEquals(listOf(false, true, true, true, false), engine.itemsHaveContent("team"))
        assertEquals(engine.itemsHaveContent("team"), (0 until 5).map { engine.itemHasContent("team", it) })
        assertFalse(engine.itemHasContent("team", 5))
        assertFalse(engine.itemHasContent("team", Int.MAX_VALUE))
        assertFalse(engine.itemHasContent("name", 0))
        assertEquals(emptyList<Boolean>(), engine.itemsHaveContent("name"))
        assertEquals(emptyList<Boolean>(), engine.itemsHaveContent("missing"))
    }

    @Test
    fun `advancesOnCommit follows a truthy autoAdvance`() {
        assertTrue(field("""{"key":"a","type":"radio","label":"A","autoAdvance":true,"options":[]}""").advancesOnCommit)
        assertFalse(field("""{"key":"a","type":"radio","label":"A","autoAdvance":false,"options":[]}""").advancesOnCommit)
        assertFalse(field("""{"key":"a","type":"radio","label":"A","options":[]}""").advancesOnCommit)
    }

    @Test
    fun `formatTemplate fills parameters and keeps unknown ones`() {
        val params = stringMapOf("count" to JsonValue.Number(3.0), "name" to text("Ada"))
        assertEquals("Ada has 3 of {max}", formatTemplate("{name} has {count} of {max}", params))
        assertEquals("no braces", formatTemplate("no braces", params))
    }

    @Test
    fun `parseDefinition reads a valid definition and rejects an invalid one`() {
        val definition = parseDefinition(JsonValue.parse(form))
        assertEquals("f", definition.id)
        assertEquals(listOf("name", "email", "team"), definition.fields.orEmpty().map { it.key })
        val error =
            assertThrows(FormDefinitionError::class.java) {
                parseDefinition(JsonValue.parse("""{"id":"f","fields":1}"""))
            }
        assertTrue(error.issues.isNotEmpty())
        assertThrows(FormDefinitionError::class.java) { parseDefinition(JsonValue.Array(emptyList())) }
    }
}
