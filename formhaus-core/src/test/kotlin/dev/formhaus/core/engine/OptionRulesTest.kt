package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.ConditionRule
import dev.formhaus.core.renderer.RendererApi
import dev.formhaus.core.renderer.fieldRules
import dev.formhaus.core.support.definition
import dev.formhaus.core.support.engine
import dev.formhaus.core.support.field
import dev.formhaus.core.support.jsonObject
import dev.formhaus.core.support.text
import dev.formhaus.core.validation.ValidateOptions
import dev.formhaus.core.validation.validateFieldValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(RendererApi::class)
class OptionRulesTest {
    private val form =
        """{"formatVersion":"1.8","id":"f","submit":{"label":"S"},"fields":[
            {"key":"seats","type":"number","label":"Seats"},
            {"key":"plan","type":"radio","label":"Plan","readOnly":[{"field":"seats","gt":100}],"options":[
                {"value":"starter","label":"Starter","description":"Small","disabled":[{"field":"seats","gt":5}]},
                {"value":"legacy","label":"Legacy","disabled":true}]},
            {"key":"people","type":"group","label":"People","reorderable":true,"fields":[
                {"key":"size","type":"number","label":"Size"},
                {"key":"role","type":"select","label":"Role","readOnly":[{"field":"size","gt":1}],"options":[
                    {"value":"lead","label":"Lead","disabled":[{"field":"size","gt":1}]}]}]}]}"""

    @Test
    fun `the model reads option descriptions, option rules, readOnly and reorderable`() {
        val plan = definition(form).allFields[1]
        assertEquals("Small", plan.options?.first()?.description)
        assertEquals(ConditionRule.Always, plan.options?.last()?.disabled)
        assertTrue(plan.readOnly is ConditionRule.When)
        assertTrue(definition(form).allFields[2].reorderable)
    }

    @Test
    fun `an older format ignores them`() {
        val plan = definition(form.replace("1.8", "1.7")).allFields[1]
        assertNull(plan.options?.first()?.description)
        assertNull(plan.options?.last()?.disabled)
        assertNull(plan.readOnly)
        assertFalse(definition(form.replace("1.8", "1.7")).allFields[2].reorderable)
    }

    @Test
    fun `field rules follow the values and the item`() {
        val engine = engine(form, """{"seats":8,"people":[{"size":1},{"size":3}]}""")
        val plan = engine.definition.allFields[1]
        val rules = engine.fieldRules(plan)
        assertFalse(rules.readOnly)
        assertTrue(plan.options.orEmpty().all(rules::isDisabled))
        val role = engine.definition.allFields[2].items[1]
        assertFalse(engine.fieldRules(role, "people.0.role").readOnly)
        assertTrue(engine.fieldRules(role, "people.1.role").readOnly)
        assertTrue(engine.fieldRules(role, "people.1.role").isDisabled(role.options.orEmpty().first()))
        engine.setValue("seats", JsonValue.Number(200.0))
        assertTrue(engine.fieldRules(plan).readOnly)
    }

    @Test
    fun `validateFieldValue keeps a disabled option the previous values hold`() {
        val legacy = field("""{"key":"plan","type":"radio","label":"Plan","options":[{"value":"legacy","label":"L","disabled":true}]}""")
        val modern = definition(form).allFields[1]
        assertNull(validateFieldValue(legacy, text("legacy"), jsonObject("{}")))
        assertEquals("Select one of the available options", validateFieldValue(modern, text("legacy"), jsonObject("{}")))
        val options = ValidateOptions(previousValues = jsonObject("""{"plan":"legacy"}"""))
        assertNull(validateFieldValue(modern, text("legacy"), jsonObject("{}"), options))
    }

    @Test
    fun `moveItem refuses a hidden group`() {
        val engine =
            engine(
                """{"formatVersion":"1.8","id":"f","submit":{"label":"S"},"fields":[
                    {"key":"on","type":"checkbox","label":"On"},
                    {"key":"list","type":"group","label":"L","show":[{"field":"on","eq":true}],"fields":[
                        {"key":"a","type":"text","label":"A"}]}]}""",
                """{"on":true,"list":[{"a":"1"},{"a":"2"}]}""",
            )
        engine.setValue("on", JsonValue.Bool(false))
        assertFalse(engine.moveItem("list", 0, 1))
        engine.setValue("on", JsonValue.Bool(true))
        assertTrue(engine.moveItem("list", 0, 1))
        assertEquals(jsonObject("""{"on":true,"list":[{"a":"2"},{"a":"1"}]}"""), engine.values)
    }

    @Test
    fun `a condition list with an entry that is not an object never matches`() {
        val broken =
            definition(
                """{"formatVersion":"1.8","id":"f","submit":{"label":"S"},"fields":[
                    {"key":"seats","type":"number","label":"Seats"},
                    {"key":"plan","type":"radio","label":"Plan","readOnly":[{"field":"seats","gt":1},5],"options":[
                        {"value":"a","label":"A","disabled":[{"field":"seats","gt":1},"x"]}]}]}""",
            )
        val plan = broken.allFields[1]
        val values = jsonObject("""{"seats":3}""")
        assertNull(validateFieldValue(plan, text("a"), values))
        val engine = FormEngine(broken, values, FormEngineOptions(onWarning = {}))
        assertFalse(engine.fieldRules(plan).readOnly)
        assertFalse(engine.fieldRules(plan).isDisabled(plan.options.orEmpty().first()))
    }
}
