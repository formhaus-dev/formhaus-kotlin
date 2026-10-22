package dev.formhaus.core.validation

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FormSection
import dev.formhaus.core.support.definition
import dev.formhaus.core.support.jsonObject
import dev.formhaus.core.visibility.evaluateCondition
import dev.formhaus.core.visibility.isActionDisabled
import dev.formhaus.core.visibility.isVisible
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValidateStepTest {
    private val steps =
        definition(
            """
        {"id":"f","title":"F","submit":{"label":"S"},"steps":[
          {"id":"one","title":"One","fields":[{"key":"a","type":"text","label":"A","validation":{"required":true}}],
           "routes":[{"to":null,"show":[{"field":"a","eq":"end"}]}]},
          {"id":"two","title":"Two","show":[{"field":"a","neq":"skip"}],
           "fields":[{"key":"s","type":"section","title":"S","showAny":[{"field":"a","eq":"x"}],"fields":[]}]}
        ]}
        """,
        )

    @Test
    fun `isVisible accepts steps, routes, sections and nodes`() {
        val list = requireNotNull(steps.steps)
        assertTrue(isVisible(list[1], jsonObject("""{"a":"x"}""")))
        assertFalse(isVisible(list[1], jsonObject("""{"a":"skip"}""")))
        assertTrue(isVisible(requireNotNull(list[0].routes).first(), jsonObject("""{"a":"end"}""")))
        val section = list[1].fields.first() as FormSection
        assertTrue(isVisible(section, jsonObject("""{"a":"x"}""")))
        assertFalse(isVisible(section, jsonObject("""{"a":"y"}""")))
    }

    @Test
    fun `conditions, disabled actions and field validation are callable on their own`() {
        val form =
            definition(
                """{"id":"f","submit":{"label":"S","disabled":[{"field":"a","eq":"lock"}]},
            "fields":[{"key":"a","type":"text","label":"A","validation":{"required":true}}]}""",
            )
        val fields = requireNotNull(form.fields)
        val condition = requireNotNull(form.submit.disabled).first()
        assertTrue(evaluateCondition(condition, jsonObject("""{"a":"lock"}""")))
        assertFalse(evaluateCondition(condition, jsonObject("""{"a":"open"}""")))
        assertTrue(isActionDisabled(form.submit, jsonObject("""{"a":"lock"}""")))
        assertFalse(isActionDisabled(form.cancel, jsonObject("""{"a":"lock"}""")))
        assertEquals(stringMapOf("a" to "This field is required"), validateFields(fields, stringMapOf()))
        assertNull(validateFieldValue(form.allFields.first(), JsonValue.String("x"), jsonObject("""{"a":"x"}""")))
        assertEquals("This field is required", validateFieldValue(form.allFields.first(), null, stringMapOf()))
    }

    @Test
    fun `numeric conditions convert strings like Number`() {
        val form = definition("""{"id":"f","submit":{"label":"S","disabled":[{"field":"n","gt":10}]},"fields":[]}""")
        val condition = requireNotNull(form.submit.disabled).first()
        for ((value, expected) in listOf(
            "\" 0x10 \"" to true,
            "\"Infinity\"" to false,
            "\"1_0\"" to false,
            "\"  \"" to false,
            "11" to true,
        )) {
            assertEquals(expected, evaluateCondition(condition, jsonObject("""{"n":$value}""")), value)
        }
    }

    @Test
    fun `steps validate shown fields, sections and group items`() {
        val form =
            definition(
                """
            {"id":"f","submit":{"label":"S"},"steps":[{"id":"one","fields":[
              {"key":"kind","type":"radio","label":"K","options":[{"value":"a","label":"A"},{"value":"b","label":"B"}]},
              {"key":"why","type":"text","label":"W","show":[{"field":"kind","eq":"b"}],"validation":{"required":true}},
              {"key":"sec","type":"section","title":"S","show":[{"field":"kind","eq":"a"}],
               "fields":[{"key":"note","type":"text","label":"N","validation":{"required":true}}]},
              {"key":"people","type":"group","label":"P","fields":[
                {"key":"name","type":"text","label":"Name","validation":{"required":true}},
                {"key":"age","type":"number","label":"Age","show":[{"field":"name","eq":"Ann"}],"validation":{"min":18}}
              ]}
            ]}]}
            """,
            )
        val step = requireNotNull(form.steps).first()
        val values =
            jsonObject("""{"kind":"a","people":[{"name":" "},{"name":"Ann","age":3},{"name":"Bob","age":3}]}""")
        assertEquals(
            stringMapOf(
                "note" to "This field is required",
                "people.0.name" to "This field is required",
                "people.1.age" to "Must be at least 18",
            ),
            validateStep(step, values),
        )
        assertEquals(listOf("why"), validateStep(step, jsonObject("""{"kind":"b"}""")).keys)
    }
}
