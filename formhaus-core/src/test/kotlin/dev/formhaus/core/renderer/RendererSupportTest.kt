package dev.formhaus.core.renderer

import dev.formhaus.core.support.field
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(RendererApi::class)
class RendererSupportTest {
    @Test
    fun `relative, blob, http, https and raster data images are kept`() {
        val allowed =
            listOf(
                "/assets/a.png",
                "./a@2x.png",
                "a.png",
                "//cdn.example.com/a.png",
                "blob:https://example.com/3f1c",
                "http://localhost:5173/a.png",
                "HTTPS://example.com/a@b.png?x=1",
                "data:image/png;base64,iVBORw0KGgo=",
                "data:image/webp,x",
            )
        assertEquals(allowed, allowed.map(::imageSource))
    }

    @Test
    fun `script, file, non-raster data and credential URLs are dropped`() {
        val refused =
            listOf(
                null,
                "",
                " ",
                "javascript:alert(1)",
                " JaVa\tScRiPt:alert(1)",
                "\u0001javascript:alert(1)",
                "vbscript:msgbox(1)",
                "data:text/html,<script>",
                "data:image/svg+xml;base64,PHN2Zz4=",
                "file:///etc/hosts",
                "https://user:pass@example.com/a.png",
                "http:user@example.com/a.png",
                "//user@example.com/a.png",
                "https://h\\@evil.example/x.png",
                "https://",
                "https://user@",
            )
        assertTrue(refused.all { imageSource(it) == null })
    }

    @Test
    fun `option texts tell repeated labels apart`() {
        val city =
            field(
                """{"key":"city","type":"select","label":"City","options":[{"value":"a","label":"Paris"},{"value":"b","label":"Paris"},
            {"value":"c","label":"Rome"},{"value":"x","label":"Lyon (y)"},{"value":"y","label":"Lyon"},{"value":"z","label":"Lyon"}]}""",
            )
        assertEquals(
            listOf("Paris (a)", "Paris (b)", "Rome", "Lyon (y) (1)", "Lyon (y) (2)", "Lyon (z)"),
            optionTexts(city.options.orEmpty()),
        )
    }

    @Test
    fun `a divider separates the first exclusive option from regular ones`() {
        val pick =
            field(
                """{"key":"pick","type":"multiselect","label":"Pick","options":[{"value":"a","label":"A"},{"value":"n","label":"None","exclusive":true}]}""",
            )
        val options = pick.options.orEmpty()
        assertFalse(dividerBefore(pick, options, 0))
        assertTrue(dividerBefore(pick, options, 1))
        assertFalse(dividerBefore(pick, options, 2))
    }

    @Test
    fun `error entries join labels by punctuation`() {
        assertEquals("Name? Taken", ErrorEntry("a", "Name?", "Taken").text)
        assertEquals("City: Missing", ErrorEntry("b", "City", "Missing").text)
        assertEquals("Zip： Bad", ErrorEntry("c", "Zip：", "Bad").text)
        assertEquals(": Empty", ErrorEntry("d", "", "Empty").text)
    }

    @Test
    fun `renderer helpers expose ids, limits and options`() {
        assertEquals("f.email", TestIdTarget(TestIdTarget.Kind.FIELD, "f", "email").defaultId)
        assertEquals("f.plan.pro", TestIdTarget(TestIdTarget.Kind.OPTION, "f", "plan", "pro").defaultId)
        assertEquals("f.submit", TestIdTarget(TestIdTarget.Kind.SUBMIT, "f").defaultId)
        assertEquals(3, clampedInt(3.9, 0..10))
        assertEquals(0, clampedInt(-2.0, 0..10))
        assertEquals(10, clampedInt(1e300, 0..10))
        assertNull(clampedInt(Double.NaN, 0..10))
        val option = FieldOption("a", "A", "data:image/svg+xml,x")
        assertEquals("a", option.value)
        assertNull(option.safeImage)
        assertEquals("data:image/svg+xml,x", option.imageKey?.source)
        assertEquals(option.imageKey, FieldOption("b", "B", "data:image/svg+xml,x").imageKey)
        assertNotEquals(FieldOption("a", "A", "Aa").imageKey, FieldOption("a", "A", "BB").imageKey)
        assertNull(FieldOption("a", "A").imageKey)
        assertTrue(field("""{"key":"a","type":"text","validation":{"required":"yes"}}""").isRequired)
    }
}
