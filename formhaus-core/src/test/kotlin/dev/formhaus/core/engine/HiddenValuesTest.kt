package dev.formhaus.core.engine

import dev.formhaus.core.support.engine
import dev.formhaus.core.support.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HiddenValuesTest {
    private val definition = """{"formatVersion":"1.5","id":"account","title":"Account","submit":{"label":"Save"},"steps":[
        {"id":"profile","title":"Profile","fields":[
          {"key":"type","type":"select","label":"Type","options":[{"value":"personal","label":"Personal"},{"value":"business","label":"Business"}]},
          {"key":"company","type":"text","label":"Company","show":[{"field":"type","eq":"business"}]},
          {"key":"greeting","type":"statement","label":"Hello {{company}}"}]},
        {"id":"extra","title":"Extra","skip":{"label":"Skip"},
         "fields":[{"key":"note","type":"text","label":"Note","show":[{"field":"type","eq":"business"}]}]}]}"""

    @Test
    fun `a write that drops a kept value before any read reports one change`() {
        val engine = engine(definition, """{"type":"personal","company":"Acme"}""", FormEngineOptions(onWarning = {}, hiddenValues = HiddenValues.KEEP))
        var changes = 0
        engine.subscribe { changes += 1 }
        engine.setValue("company", null)
        assertEquals(1, changes)
        assertNull(engine.values["company"])
        assertEquals(jsonObject("""{"type":"personal"}"""), engine.getSubmitValues())
    }
}
