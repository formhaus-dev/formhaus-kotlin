package dev.formhaus.core.model

import dev.formhaus.core.json.JsonValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelDecodingTest {
    private fun definition(text: String) = FormDefinition(requireNotNull(JsonValue.parse(text).objectValue))

    @Test
    fun `steps, routes, buttons and sections decode`() {
        val form = definition(
            """
            {"id":"f","title":"F","submit":{"label":"S"},"steps":[
              {"id":"one","title":"One","next":{"label":"Go"},"back":false,"skip":true,
               "fields":[{"key":"a","type":"text","label":"A","validation":{"required":true}}],
               "routes":[{"to":null,"show":[{"field":"a","eq":"end"}]},{"to":"two"},3]},
              {"id":"two","show":[{"field":"a","neq":"skip"}],
               "fields":[{"key":"s","type":"section","title":"S","showAny":[{"field":"a","eq":"x"}],
                 "fields":[{"key":"b","type":"text"},"junk"]}]},
              "junk"
            ]}
            """,
        )
        val steps = requireNotNull(form.steps)
        assertEquals(listOf("one", "two"), steps.map { it.id })
        assertTrue(form.isMultiStep)
        val one = steps[0]
        assertEquals("Go", one.next.action?.label)
        assertEquals(StepButton.Hidden, one.back)
        assertEquals(StepButton.Absent, one.skip)
        assertTrue(one.canSkip)
        val routes = requireNotNull(one.routes)
        assertEquals(2, routes.size)
        assertTrue(routes[0].endsForm)
        assertEquals("eq", routes[0].show?.single()?.operatorName)
        assertEquals("two", routes[1].to)
        assertFalse(routes[1].endsForm)
        val section = steps[1].fields.single() as FormSection
        assertEquals("section", section.type)
        assertEquals(listOf("a"), section.conditionFields)
        assertEquals(listOf("a", "b"), form.allFields.map { it.key })
        assertEquals(listOf(1, 4), form.allFields.map { it.ordinal })
        assertEquals(listOf(0, 2), steps.map { it.ordinal })
        assertEquals(3, section.ordinal)
        assertNull(form.fields)
    }

    @Test
    fun `scale, rating, picture options and matrix sections expose their settings`() {
        val form = definition(
            """
            {"formatVersion":"1.3","id":"f","title":"F","submit":{"label":"S"},"fields":[
              {"key":"nps","type":"scale","label":"N","validation":{"min":0,"max":10}},
              {"key":"stars","type":"rating","label":"R","validation":{"min":3}},
              {"key":"pick","type":"radio","label":"P","columns":2,
               "options":[{"value":"a","label":"A","image":"https://example.com/a.png"},{"value":1},{"value":0},{"value":"a"}]},
              {"key":"grid","type":"section","layout":"matrix","title":"G","fields":[]}
            ]}
            """,
        )
        val nodes = requireNotNull(form.fields)
        val nps = nodes[0] as FormField
        val stars = nodes[1] as FormField
        val pick = nodes[2] as FormField
        assertEquals(ScaleRange(0.0, 10.0), nps.scaleRange)
        assertEquals(ScaleRange(1.0, 5.0), stars.scaleRange)
        assertTrue(stars.isScale)
        assertNull(pick.scaleRange)
        assertEquals(2, pick.columns)
        assertEquals("https://example.com/a.png", pick.options?.first()?.image)
        assertTrue(pick.hasPictures)
        val index = requireNotNull(pick.optionIndex)
        assertEquals(0, index.position(JsonValue.String("a")))
        assertEquals(1, index.position(JsonValue.Number(1)))
        assertEquals(2, index.position(JsonValue.Number(-0.0)))
        assertNull(index.position(JsonValue.String("1")))
        val grid = nodes[3] as FormSection
        assertTrue(grid.isMatrix)
        assertFalse(form.recallsAnswers)
        assertEquals("1.6", FormDefinition.SUPPORTED_FORMAT_VERSION)
    }

    @Test
    fun `groups own their item fields and lenient values fall back`() {
        val form = definition(
            """
            {"formatVersion":"1.5","id":7,"submit":"x","fields":[
              {"key":"g","type":"group","fields":[{"key":"x","type":"text"},{"key":"y","type":"section","fields":[]}]},
              {"key":"t","type":"text","fields":[{"key":"ignored","type":"text"}],"columns":2.5}
            ]}
            """,
        )
        val nodes = requireNotNull(form.fields)
        val group = nodes[0] as FormField
        val text = nodes[1] as FormField
        assertEquals("", form.id)
        assertNull(form.submit.label)
        assertTrue(form.recallsAnswers)
        assertTrue(group.isGroup)
        assertEquals(listOf("x"), group.items.map { it.key })
        assertNull(text.fields)
        assertNull(text.columns)
        assertEquals(3, text.ordinal)
    }

    @Test
    fun `format versions split like the reference`() {
        assertEquals(1.0 to 0.0, formatParts(null))
        assertEquals(1.0 to 6.0, formatParts(JsonValue.String("1.6")))
        assertEquals(2.0 to 0.0, formatParts(JsonValue.Number(2)))
        assertTrue(formatParts(JsonValue.String("x.y")).first.isNaN())
    }
}
