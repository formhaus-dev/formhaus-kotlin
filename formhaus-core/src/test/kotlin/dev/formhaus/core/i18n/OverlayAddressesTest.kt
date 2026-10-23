package dev.formhaus.core.i18n

import dev.formhaus.core.json.JsonValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OverlayAddressesTest {
    private val definition =
        JsonValue.parse(
            """{"id":"trip","title":"Trip","submit":{"label":"Book"},"steps":[
            {"id":"who","title":"Who","fields":[
              {"key":"name","type":"text","label":"Name","validation":{"required":"Enter a name"}},
              {"key":"city","type":"select","label":"City","options":[{"value":"rome","label":"Rome"}]}]},
            {"id":"guests","title":"Guests","fields":[
              {"key":"people","type":"group","label":"Guests","fields":[{"key":"age","type":"number","label":"Age"}]}]}]}""",
        )

    private val overlay =
        JsonValue.parse(
            """{"fields":{
              "name":{"label":"Nom","validation":{"required":"Saisissez un nom"}},
              "city":{"label":"Ville","options":{"rome":"Rome (Italie)"}},
              "people":{"label":"Invités","fields":{"age":{"label":"Âge"}}}},
            "steps":{"who":{"fields":{"city":{"label":"Destination"}}}}}""",
        )

    @Test
    fun `applies fields at the top level and lets the step address win`() {
        val steps = requireNotNull(localize(definition, overlay).definition.steps)
        val (name, city) = steps[0].fields.map { JsonValue.Object(it.json) }
        assertEquals("Nom", name["label"]?.stringValue)
        assertEquals("Saisissez un nom", name["validation"]?.get("required")?.stringValue)
        assertEquals("Destination", city["label"]?.stringValue)
        assertEquals(
            "Rome (Italie)",
            city["options"]
                ?.arrayValue
                ?.first()
                ?.get("label")
                ?.stringValue,
        )
        val group = JsonValue.Object(steps[1].fields[0].json)
        assertEquals("Invités", group["label"]?.stringValue)
        assertEquals(
            "Âge",
            group["fields"]
                ?.arrayValue
                ?.first()
                ?.get("label")
                ?.stringValue,
        )
    }
}
