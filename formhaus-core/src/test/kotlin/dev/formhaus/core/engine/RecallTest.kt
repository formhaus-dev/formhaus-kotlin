package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.renderer.RendererApi
import dev.formhaus.core.renderer.autocomplete
import dev.formhaus.core.renderer.dividerBefore
import dev.formhaus.core.renderer.errorGroups
import dev.formhaus.core.renderer.itemTitle
import dev.formhaus.core.support.definition
import dev.formhaus.core.support.engine
import dev.formhaus.core.support.field
import dev.formhaus.core.support.text
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(RendererApi::class)
class RecallTest {
    private fun formatted(
        answer: RecallAnswer,
        type: String,
        locale: String = "en",
    ): String = defaultRecallFormat(answer, field("""{"key":"a","type":"$type","label":"A"}"""), locale)

    @Test
    fun `the default formatter uses the platform styles in UTC`() {
        assertEquals("Jun 14, 2027", formatted(RecallAnswer.Text("2027-06-14"), "date"))
        assertTrue(formatted(RecallAnswer.Text("13:05"), "time").startsWith("1:05"))
        assertTrue(formatted(RecallAnswer.Text("2027-06-14T13:05+09:00"), "datetime").startsWith("Jun 14, 2027"))
        assertEquals("1,234.5", formatted(RecallAnswer.Number(1234.5), "number"))
        assertEquals("0.1", formatted(RecallAnswer.Number(0.1), "number"))
        assertEquals("0", formatted(RecallAnswer.Number(-0.0), "scale"))
        assertEquals("3", formatted(RecallAnswer.Number(3.0), "group"))
        assertEquals(
            "Red, Green, and Blue",
            formatted(RecallAnswer.Items(listOf("Red", "Green", "Blue")), "multiselect"),
        )
        assertEquals("1.234,5", formatted(RecallAnswer.Number(1234.5), "number", "de"))
        assertTrue("2027" in formatted(RecallAnswer.Text("2027-06-14"), "date", "de"))
    }

    @Test
    fun `the default formatter falls back to the stored text and to en`() {
        assertEquals("2027-02-30", formatted(RecallAnswer.Text("2027-02-30"), "date"))
        assertEquals("1e+21", formatted(RecallAnswer.Number(1e21), "number"))
        assertEquals("1234.5", formatted(RecallAnswer.Number(1234.5), "text"))
        assertEquals("a b", formatted(RecallAnswer.Text("a \n\t b"), "text"))
        assertEquals("1,234.5", formatted(RecallAnswer.Number(1234.5), "number", "not a locale!"))
        assertEquals("1,234.5", formatted(RecallAnswer.Number(1234.5), "number", "zz"))
        assertEquals("1,234.5", formatted(RecallAnswer.Number(1234.5), "number", ""))
    }

    @Test
    fun `item titles and error labels resolve recall in one pass with their parameters`() {
        val engine =
            engine(
                """{"formatVersion":"1.5","id":"f","title":"Form for {{host}}","submit":{"label":"S"},"fields":[
                {"key":"host","type":"text","label":"Host"},
                {"key":"people","type":"group","label":"Guests of {{host}}","itemLabel":"{{name}} #{index} of {label}",
                 "fields":[{"key":"name","type":"text","label":"Name of {{name}}","validation":{"required":true}}]}]}""",
                """{"host":"{index}","people":[{"name":"Bo"},{}]}""",
            )
        val group = engine.definition.allFields.last()
        assertEquals("Bo #1 of Guests of {index}", engine.itemTitle(group, 0, null))
        engine.validate()
        val errors = engine.errorGroups(null)
        assertEquals("Form for {index}", errors.first().title)
        assertEquals(
            "Name of  ( #2 of Guests of {index})",
            errors
                .first()
                .entries
                .first()
                .label,
        )
    }

    @Test
    fun `recallStatic shows labels and item keys of a group`() {
        val form =
            definition(
                """{"formatVersion":"1.5","id":"f","submit":{"label":"S"},"fields":[
                {"key":"name","type":"text","label":"Name"},{"key":"plain","type":"text","label":""},
                {"key":"people","type":"group","label":"People","fields":[{"key":"guest","type":"text","label":"Guest"}]}]}""",
            )
        assertEquals("Name, plain, , {{name}}", recallStatic(form, "{{name}}, {{ plain }}, {{nope}}, \\{{name}}"))
        assertEquals("Guest of People", recallStatic(form, "{{guest}} of {{people}}", group = "people"))
    }

    @Test
    fun `recall stays off below format 1_5 and texts are cut at 10,000 code units`() {
        val old = definition("""{"id":"f","submit":{"label":"S"},"fields":[{"key":"a","type":"text","label":"A"}]}""")
        assertEquals("{{a}}", recallText(old, "{{a}}", stringMapOf("a" to text("x"))))
        val form =
            definition(
                """{"formatVersion":"1.5","id":"f","submit":{"label":"S"},"fields":[{"key":"a","type":"text","label":"A"}]}""",
            )
        val value = "a".repeat(9999) + "😀"
        val recalled =
            recallText(
                form,
                "{{a}}",
                stringMapOf("a" to text(value)),
                RecallOptions(format = {
                    answer,
                    _,
                    _,
                    ->
                    answer.stored
                }),
            )
        assertEquals(9999, recalled.length)
    }

    @Test
    fun `renderers get autofill sections and exclusive dividers`() {
        val engine =
            engine(
                """{"formatVersion":"1.5","id":"f","submit":{"label":"S"},"fields":[
                {"key":"guest_list","type":"group","label":"Guests","fields":[
                  {"key":"name","type":"text","label":"Name","autocomplete":" name "},
                  {"key":"home","type":"text","label":"Home","autocomplete":"Section-home street-address"},
                  {"key":"off","type":"text","label":"Off","autocomplete":"off"}]},
                {"key":"pick","type":"multiselect","label":"Pick","options":[{"value":"a","label":"A"},{"value":"n","label":"None","exclusive":true}]}]}""",
            )
        val fields =
            engine.definition.allFields
                .first()
                .items
        assertEquals("section-guest_5f_list-1 name", engine.autocomplete(fields[0], "guest_list.1.name"))
        assertEquals("section-guest_5f_list-1-home street-address", engine.autocomplete(fields[1], "guest_list.1.home"))
        assertEquals("off", engine.autocomplete(fields[2], "guest_list.1.off"))
        assertEquals(" name ", engine.autocomplete(fields[0]))
        val pick = engine.definition.allFields.last()
        val options = pick.options.orEmpty()
        assertFalse(dividerBefore(pick, options, 0))
        assertTrue(dividerBefore(pick, options, 1))
    }

    @Test
    fun `an item field hidden in its item recalls nothing while its value is kept`() {
        val engine =
            engine(
                """{"formatVersion":"1.5","id":"f","title":"F","submit":{"label":"Send"},"fields":[
                {"key":"type","type":"text","label":"Type"},
                {"key":"company","type":"text","label":"Company","show":[{"field":"type","eq":"business"}]},
                {"key":"people","type":"group","label":"People","fields":[
                  {"key":"guest","type":"text","label":"Guest"},{"key":"vegan","type":"checkbox","label":"Vegan"},
                  {"key":"meal","type":"text","label":"Meal","show":[{"field":"vegan","eq":false}]},
                  {"key":"note","type":"text","label":"Meal [{{meal}}]"}]},
                {"key":"summary","type":"text","label":"Company [{{company}}]"}]}""",
                """{"type":"business","company":"Acme","people":[{"guest":"Bo","vegan":false,"meal":"Soup"}]}""",
                FormEngineOptions(onWarning = {}, hiddenValues = HiddenValues.KEEP),
            )
        val item = RecallOptions(RecallItem("people", 0))
        assertEquals("Meal [Soup]", engine.recallText("Meal [{{meal}}]", item))
        assertEquals("Company [Acme]", engine.recallText("Company [{{company}}]"))
        engine.setItemValue("people", 0, "vegan", JsonValue.Bool(true))
        engine.setValue("type", text("personal"))
        assertEquals(JsonValue.parse("""[{"guest":"Bo","vegan":true,"meal":"Soup"}]"""), engine.values["people"])
        assertEquals(text("Acme"), engine.values["company"])
        assertEquals("Meal []", engine.recallText("Meal [{{meal}}]", item))
        assertEquals("Company []", engine.recallText("Company [{{company}}]"))
    }
}
