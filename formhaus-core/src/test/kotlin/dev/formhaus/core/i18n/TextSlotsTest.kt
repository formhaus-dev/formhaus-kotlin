package dev.formhaus.core.i18n

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.support.Fixtures
import dev.formhaus.core.support.definition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class TextSlotsTest {
    private val form = """
        {"id":"signup","title":"Sign up","submit":{"label":"Send"},"steps":[
          {"id":"account","title":"Account","fields":[
            {"key":"email","type":"email","label":"Email","i18n":{"label":"auth.email"},"validation":{"required":true,"maxLength":40}},
            {"key":"plan","type":"radio","label":"Plan","options":[{"value":"pro","label":"Pro {tier}"}]}
          ]}
        ]}
    """

    @TestFactory
    fun `conformance texts`(): List<DynamicTest> = Fixtures.conformanceNames.mapNotNull { name ->
        val file = Fixtures.conformanceFile(name)
        val texts = file["texts"]?.objectValue ?: return@mapNotNull null
        DynamicTest.dynamicTest(name) {
            val parsed = FormDefinition(requireNotNull(file["definition"]?.objectValue))
            val overlay = file["overlay"]
            val rejected = file["issues"]?.arrayValue.orEmpty().isNotEmpty()
            val slots = textSlots(if (rejected || overlay == null) parsed else applyOverlay(parsed, overlay).definition)
            assertEquals(slots.size, slots.map { it.address }.toSet().size, "duplicate text slot addresses")
            for ((address, text) in texts) {
                assertEquals(text, slots.firstOrNull { it.address == address }?.let { JsonValue.String(it.text) } ?: JsonValue.Null, address)
            }
        }
    }

    @Test
    fun `slots carry keys, params and stable hashes`() {
        val slots = textSlots(definition(form)).associateBy { it.address }
        assertEquals("auth.email", slots.getValue("steps.account.fields.email.label").key)
        assertEquals("signup.steps.account.title", slots.getValue("steps.account.title").key)
        assertEquals(listOf("tier"), slots.getValue("steps.account.fields.plan.options.pro").params)
        assertEquals(listOf("current", "total"), slots.getValue("labels.stepOf").params)
        assertTrue(slots.values.all { Regex("[0-9a-f]{8}").matches(it.hash) })
        assertEquals(Integer.toHexString(fnv1a("auth.email\u0000Email")).padStart(8, '0'), slots.getValue("steps.account.fields.email.label").hash)
        assertTrue("steps.account.fields.email.validation.maxLength" !in slots)
        val bare = textSlots(definition(form), TranslationPrefix.None).associateBy { it.address }
        assertEquals(null, bare.getValue("title").key)
        assertEquals("x.title", textSlots(definition(form), TranslationPrefix.Custom("x")).first { it.address == "title" }.key)
    }

    @Test
    fun `an overlay replaces texts, formats rules and fills catalogs`() {
        val overlay = JsonValue.parse(
            """{"title":"Anmelden","steps":{"account":{"fields":{"email":{"label":"E-Mail","validation":{"maxLength":"Max {max}"}},
               "plan":{"options":{"pro":"Profi"}}}}},"messages":{"required":"Pflichtfeld"},"labels":{"submit":" "}}""",
        )
        val localized = applyOverlay(definition(form), overlay, lang = "de")
        val result = localized.definition
        assertEquals("Anmelden", result.title)
        assertEquals("de", result.lang)
        val email = result.allFields.first()
        assertEquals("E-Mail", email.label)
        assertEquals("Max 40", email.validation?.message("maxLengthMessage"))
        assertEquals("Profi", result.allFields[1].options?.first()?.label)
        assertEquals(JsonValue.parse("""{"required":"Pflichtfeld"}"""), localized.messages)
        assertEquals(JsonValue.parse("{}"), localized.labels)
    }

    @Test
    fun `missingKeys lists the built-in keys a catalog lacks`() {
        val found = missingKeys(
            messages = dev.formhaus.core.validation.MessageSource.Catalog(JsonValue.parse("""{"required":"Pflichtfeld","email":""}""")),
            labels = dev.formhaus.core.renderer.LabelSource.Catalog(JsonValue.parse("""{"continue":"Weiter"}""")),
        )
        assertTrue(dev.formhaus.core.validation.MessageKey.REQUIRED !in found.messages)
        assertTrue(dev.formhaus.core.validation.MessageKey.EMAIL in found.messages)
        assertTrue(dev.formhaus.core.validation.MessageKey.MIN_LENGTH in found.messages)
        assertTrue("continue" !in found.labels && "back" in found.labels)
        val none = missingKeys(messages = dev.formhaus.core.validation.MessageSource.Function { _, _, _ -> null })
        assertTrue(none.messages.isEmpty() && none.labels.isEmpty())
    }
}
