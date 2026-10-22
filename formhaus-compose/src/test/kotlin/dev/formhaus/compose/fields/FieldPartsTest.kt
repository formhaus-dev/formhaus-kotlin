package dev.formhaus.compose.fields

import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.model.MainDispatcherRule
import dev.formhaus.compose.model.model
import dev.formhaus.core.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class FieldPartsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun kind(field: String): InputKind = InputKind.of(model(definition(field)).structure.visibleFields.first())

    @Test
    fun inputKindsMapToKeyboardsAndAutofill() {
        val email = kind("""{"key": "e", "type": "email", "label": "E"}""")
        assertEquals(KeyboardType.Email, email.keyboardOptions(false).keyboardType)
        assertEquals(KeyboardCapitalization.None, email.keyboardOptions(false).capitalization)
        assertFalse(email.keyboardOptions(false).autoCorrectEnabled == true)
        assertEquals(ContentType.EmailAddress, email.contentType)
        assertEquals(KeyboardType.Phone, kind("""{"key": "p", "type": "phone", "label": "P"}""").keyboardType)
        assertEquals(ContentType.Password, kind("""{"key": "p", "type": "password", "label": "P"}""").contentType)
        assertEquals(KeyboardType.Decimal, kind("""{"key": "n", "type": "number", "label": "N"}""").keyboardType)
        assertEquals(KeyboardType.Number, kind("""{"key": "n", "type": "number", "label": "N", "inputMode": "numeric"}""").keyboardType)
        assertEquals(KeyboardType.Uri, kind("""{"key": "u", "type": "text", "label": "U", "inputMode": "url"}""").keyboardType)
        val text = kind("""{"key": "t", "type": "text", "label": "T"}""").keyboardOptions(true)
        assertEquals(KeyboardCapitalization.Sentences, text.capitalization)
        assertEquals(ImeAction.Done, text.imeAction)
        assertEquals(ImeAction.Next, InputKind.TEXT.keyboardOptions(false).imeAction)
    }

    @Test
    fun datesParseStrictlyAndDisplayInTheLocale() {
        assertEquals(LocalDateTime.of(1990, 5, 17, 0, 0), DateKind.DATE.date("1990-05-17"))
        assertNull(DateKind.DATE.date("1990-02-30"))
        assertEquals(LocalDateTime.of(2026, 10, 19, 9, 15), DateKind.DATETIME.date("2026-10-19T09:15:30Z"))
        assertEquals("14:30", DateKind.TIME.text(requireNotNull(DateKind.TIME.date("14:30"))))
        assertEquals("May 17, 1990", DateKind.DATE.display("1990-05-17", Locale.US))
        assertTrue(requireNotNull(DateKind.DATE.display("2026-10-22", Locale.forLanguageTag("th-TH"))).contains("2569"))
        assertEquals("mm/dd/yyyy", DateKind.DATE.pattern(Locale.US))
        assertEquals("--:--", DateKind.TIME.pattern(Locale.US))
        assertEquals(DateKind.DATETIME, DateKind.of("datetime"))
    }

    @Test
    fun scalesClampTheirRangeAndNameTheirAnchors() {
        val form =
            model(
                definition(
                    """{"key": "nps", "type": "scale", "label": "N", "validation": {"min": 0, "max": 10},
                        "options": [{"value": "0", "label": "Never"}, {"value": "5", "label": "Maybe"}, {"value": "10", "label": "Always"}]}""",
                ),
            )
        val nps = ScalePoints(FieldContext(form.structure.visibleFields.first(), form))
        assertEquals((0..10).toList(), nps.points)
        assertEquals("Never", nps.anchor(0))
        assertEquals("Maybe", nps.anchor(5))
        assertNull(nps.anchor(3))
        assertEquals(0, nps.point(0))
        assertEquals(7, nps.point(7))
        assertEquals(9, nps.selected(JsonValue.Number(9.0)))
        assertNull(nps.selected(JsonValue.Number(9.5)))
        assertNull(nps.selected(JsonValue.Number(11.0)))
    }
}
