package dev.formhaus.core.engine

import dev.formhaus.core.definitionvalidation.DefinitionIssue
import dev.formhaus.core.definitionvalidation.IssueCode
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.renderer.LabelSource
import dev.formhaus.core.renderer.RendererApi
import dev.formhaus.core.renderer.label
import dev.formhaus.core.support.engine
import dev.formhaus.core.validation.MessageSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(RendererApi::class)
class MissingTextTest {
    private val labels = LabelSource.Catalog(JsonValue.parse("""{"continue":"Weiter"}"""))
    private val messages = MessageSource.Catalog(JsonValue.parse("""{"required":"Pflichtfeld"}"""))

    @Test
    fun `a shown message and label absent from the catalogs warn once per key`() {
        val issues = ArrayList<DefinitionIssue>()
        val engine = engine(
            """{"id":"w","title":"W","submit":{"label":"Send"},"fields":[
                {"key":"at","type":"time","label":"At"},{"key":"name","type":"text","label":"Name"}]}""",
            """{"at":"late"}""",
            FormEngineOptions(onWarning = { issues.add(it) }, messages = messages),
        )
        engine.validate()
        engine.validate()
        engine.label("missingTextProbe", labels)
        engine.label("missingTextProbe", labels)
        engine.label("continue", labels)
        engine.label("missingTextProbe", null)
        assertEquals(
            listOf(
                "missing-message-key /: The messages catalog has no \"time\" text. Add \"time\" to the messages catalog.",
                "missing-label-key /: The labels catalog has no \"missingTextProbe\" text. Add \"missingTextProbe\" to the labels catalog.",
            ),
            issues.map { "${it.code.rawValue} ${it.message}" },
        )
    }

    @Test
    fun `texts the definition supplies do not warn`() {
        val issues = ArrayList<DefinitionIssue>()
        val engine = engine(
            """{"id":"d","title":"D","submit":{"label":"Send"},"messages":{"date":"Datum ungültig"},"labels":{"missingTextDefined":"Definiert"},
               "fields":[{"key":"on","type":"date","label":"On"}]}""",
            """{"on":"soon"}""",
            FormEngineOptions(onWarning = { issues.add(it) }, messages = messages),
        )
        assertEquals("Datum ungültig", engine.validate()["on"])
        assertEquals("Definiert", engine.label("missingTextDefined", labels))
        assertTrue(issues.none { it.code == IssueCode.MISSING_MESSAGE_KEY || it.code == IssueCode.MISSING_LABEL_KEY })
    }
}
