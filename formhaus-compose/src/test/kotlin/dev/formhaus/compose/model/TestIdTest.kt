package dev.formhaus.compose.model

import dev.formhaus.core.renderer.TestIdTarget
import dev.formhaus.core.renderer.TestIdTarget.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

private const val IDENTIFIED =
    """{"id": "t", "title": "T", "submit": {"label": "Send"}, "fields": [{"key": "swatch", "type": "x-swatch", "label": "Swatch"}]}"""

class TestIdTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun automaticTestIdsFollowTheDefinitionId() {
        val form = model(IDENTIFIED, options = FormRendererOptions(testIds = TestIds.Automatic))
        assertEquals("t.form", form.testId(Kind.FORM))
        assertEquals("t.swatch", form.testId(Kind.FIELD, "swatch"))
        assertEquals("t.swatch.pro", form.testId(Kind.OPTION, "swatch", "pro"))
        assertEquals("t.people.0.name", form.testId(Kind.FIELD, "people.0.name"))
        assertEquals("t.summary", form.testId(Kind.SUMMARY))
        assertEquals(ActionTestIds("t.submit", "t.next", "t.back", "t.cancel"), form.actionTestIds)
        assertEquals("t.summary", TestIdTarget(Kind.SUMMARY, "t").defaultId)
    }

    @Test
    fun testIdsCanBeNamedByAFunctionAndAreOffByDefault() {
        val named =
            model(
                IDENTIFIED,
                options = FormRendererOptions(testIds = TestIds.Custom { if (it.kind == Kind.FIELD) "field-${it.key}" else null }),
            )
        assertEquals("field-swatch", named.testId(Kind.FIELD, "swatch"))
        assertNull(named.testId(Kind.OPTION, "swatch", "a"))
        val off = model(IDENTIFIED)
        assertNull(off.testId(Kind.FIELD, "swatch"))
        assertNull(off.actionTestIds.submit)
    }
}
