package dev.formhaus.core.validation

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.support.field
import dev.formhaus.core.support.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ValidateFieldTest {
    private fun error(
        fieldJson: String,
        value: String,
        values: String = "{}",
    ): String? = validateFieldValue(field(fieldJson), JsonValue.parse(value), jsonObject(values))

    @Test
    fun `message keys are an open string set compared by code units`() {
        assertEquals(MessageKey.MIN_LENGTH, MessageKey("minLength"))
        assertNotEquals(MessageKey("café"), MessageKey("café"))
        assertEquals("Must be at least 2", getDefaultMessage(MessageKey.MIN, stringMapOf("min" to JsonValue.Number(2))))
        assertEquals(
            "At most 3 words",
            getDefaultMessage(
                MessageKey.MAX_LENGTH,
                stringMapOf(
                    "max" to JsonValue.Number(3),
                    "unit" to JsonValue.String("words"),
                ),
            ),
        )
        assertEquals("Invalid value", getDefaultMessage(MessageKey("unknown")))
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "date|\"2024-02-29\"|",
            "date|\"2023-02-29\"|Enter a valid date",
            "date|\"2023-04-31\"|Enter a valid date",
            "datetime|\"2024-12-31T23:59:59.999+05:30\"|",
            "datetime|\"2023-02-29T10:00\"|Enter a valid date and time",
            "time|\"23:59\"|",
            "time|\"24:00\"|Enter a valid time",
            "email|\"a@b.co\"|",
            "email|\"a@b\"|Enter a valid email",
            "number|\"12\"|Enter a number",
            "number|12|",
            "checkbox|\"yes\"|Must be true or false",
            "text|5|Must be text",
            "select|\"z\"|Select one of the available options",
            "select|\"a\"|",
        ],
    )
    fun `built-in types check their formats`(
        type: String,
        value: String,
        expected: String?,
    ) {
        assertEquals(
            expected,
            error("""{"key":"a","type":"$type","label":"A","options":[{"value":"a","label":"A"}]}""", value),
        )
    }

    @Test
    fun `required, bounds, pattern and matchField resolve their messages`() {
        val required = """{"key":"a","type":"text","label":"A","validation":{"required":"Fill it"}}"""
        assertEquals("Fill it", error(required, "\"  \""))
        assertEquals(
            "This field is required",
            error("""{"key":"a","type":"checkbox","label":"A","validation":{"required":true}}""", "false"),
        )
        assertEquals(
            "Minimum length is 3",
            error("""{"key":"a","type":"text","label":"A","validation":{"minLength":3}}""", "\"ab\""),
        )
        assertEquals(
            "Must be at most 5",
            error("""{"key":"a","type":"number","label":"A","validation":{"max":5}}""", "6"),
        )
        assertEquals(
            "Invalid format",
            error("""{"key":"a","type":"text","label":"A","validation":{"pattern":"^\\d+$"}}""", "\"x1\""),
        )
        assertNull(error("""{"key":"a","type":"number","label":"A","validation":{"pattern":"^\\d+$"}}""", "15"))
        val match = """{"key":"b","type":"text","label":"B","validation":{"matchField":"a"}}"""
        assertEquals("Fields must match", error(match, "\"x\"", """{"a":"y"}"""))
        assertEquals("Fields must match", error(match, "\"\"", """{"a":"y"}"""))
        assertNull(error(match, "\"y\"", """{"a":"y"}"""))
    }

    @Test
    fun `lists count items and an exclusive choice drops minLength`() {
        val pick = """{"key":"p","type":"multiselect","label":"P","validation":{"minLength":2},
            "options":[{"value":"a","label":"A"},{"value":"b","label":"B"},{"value":"n","label":"None","exclusive":true}]}"""
        assertEquals("At least 2 required", error(pick, "[\"a\"]"))
        assertNull(error(pick, "[\"n\"]"))
        assertEquals("Select \"None\" or other options, not both", error(pick, "[\"a\",\"n\"]"))
        assertEquals("Must be a list of available options", error(pick, "[\"a\",\"a\"]"))
        assertEquals("Select one of the available options", error(pick, "[\"z\"]"))
    }

    @Test
    fun `hidden fields name themselves in their messages`() {
        val hidden = """{"key":"ref","type":"hidden","label":"Referrer","validation":{"maxLength":2}}"""
        assertEquals("Referrer: Maximum length is 2", error(hidden, "\"abc\""))
        assertEquals("ref: Must be text", error("""{"key":"ref","type":"hidden"}""", "1"))
    }

    @Test
    fun `message sources resolve before the defaults`() {
        val required = field("""{"key":"a","type":"text","label":"A","validation":{"required":true}}""")
        val byFunction =
            ValidateOptions(
                messages =
                    MessageSource.Function { key, _, field ->
                        if (key ==
                            MessageKey.REQUIRED
                        ) {
                            "Need ${field.key}"
                        } else {
                            null
                        }
                    },
            )
        assertEquals("Need a", validateFieldValue(required, null, stringMapOf(), byFunction))
        val catalog = MessageSource.Catalog(JsonValue.parse("""{"required":"Pflichtfeld {x}"}"""))
        assertEquals(
            "Pflichtfeld {x}",
            validateFieldValue(required, null, stringMapOf(), ValidateOptions(messages = catalog)),
        )
    }

    @Test
    fun `custom validators run last and skip empty values`() {
        val custom = field("""{"key":"a","type":"text","label":"A","validation":{"validator":"even"}}""")
        val options =
            ValidateOptions(
                validators =
                    stringMapOf<ValidatorFn>(
                        "even" to { value, _ -> if (value.stringValue!!.length % 2 == 0) null else "Odd" },
                    ),
            )
        assertEquals("Odd", validateFieldValue(custom, JsonValue.String("abc"), stringMapOf(), options))
        assertNull(validateFieldValue(custom, JsonValue.String("ab"), stringMapOf(), options))
        assertNull(validateFieldValue(custom, null, stringMapOf(), options))
    }

    @Test
    fun `message templates format numbers like JavaScript and keep unknown placeholders`() {
        assertEquals("5 {b} 1.5 null", formatText("{a} {b} {c} {d}", jsonObject("""{"a":5,"c":1.5,"d":null}""")))
        assertEquals("{ a} {} {a", formatText("{ a} {} {a", jsonObject("""{"a":1}""")))
    }
}
