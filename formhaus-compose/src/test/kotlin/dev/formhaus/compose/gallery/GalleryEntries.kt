package dev.formhaus.compose.gallery

import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.json
import dev.formhaus.compose.model.model
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.toJsonString

enum class FieldStateKind { EMPTY, FILLED, ERROR, DISABLED }

class GalleryEntry(
    val name: String,
    val field: String,
    val value: String?,
    val states: List<FieldStateKind> = FieldStateKind.entries,
) {
    override fun toString(): String = name
}

private const val PLAN_OPTIONS =
    """[{"value": "free", "label": "Free"}, {"value": "pro", "label": "Pro, billed monthly"},
       {"value": "team", "label": "Team with shared workspaces and admin controls"}]"""

const val SWATCH =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAGCAIAAABxZ0isAAAAgUlEQVR4nA3JoRHAIAwAwF6GyBAZIk" +
        "PkrhtwWYAN2IAN2ABVG3R0NBqNxpS3/7xwEuwMq8CsEA28wzD4nhdPwp1xFZwVo6F3HIY36CTamVahWSkaeadhdINP4p15FZ6Vo7" +
        "F3HsY35CTZWVaRWSWaeJdhckNP0p11FZ1Vo6l3HabfDxk9TAn8Inp8AAAAAElFTkSuQmCC"

private val pictures = """[{"value": "a", "label": "Violet", "image": "$SWATCH"}, {"value": "b", "label": "Graphite", "image": "$SWATCH"},
    {"value": "c", "label": "Sand", "image": "$SWATCH"}]"""

private const val SIZES =
    """[{"value": "s", "label": "Small"}, {"value": "m", "label": "Medium"}, {"value": "l", "label": "Large"}, {"value": "xl", "label": "Extra large"}]"""

val galleryEntries: List<GalleryEntry> =
    listOf(
        GalleryEntry(
            "text",
            """{"key": "name", "type": "text", "label": "Full name", "placeholder": "Ada Lovelace", "helperText": "As on your passport"}""",
            "\"Grace Hopper\"",
        ),
        GalleryEntry("email", """{"key": "email", "type": "email", "label": "Email"}""", "\"grace@navy.mil\""),
        GalleryEntry(
            "phone",
            """{"key": "phone", "type": "phone", "label": "Phone", "placeholder": "+1 555 000 0000"}""",
            "\"+1 555 0100\"",
        ),
        GalleryEntry("number", """{"key": "age", "type": "number", "label": "Age"}""", "42"),
        GalleryEntry("password", """{"key": "pw", "type": "password", "label": "Password"}""", "\"hunter22\""),
        GalleryEntry(
            "textarea",
            """{"key": "bio", "type": "textarea", "label": "About you", "placeholder": "A few words"}""",
            "\"Rear admiral, computer scientist and the mother of COBOL.\"",
        ),
        GalleryEntry(
            "autocomplete",
            """{"key": "city", "type": "autocomplete", "label": "City", "placeholder": "Start typing", "options": $PLAN_OPTIONS}""",
            "\"Berlin\"",
        ),
        GalleryEntry(
            "select",
            """{"key": "plan", "type": "select", "label": "Plan", "placeholder": "Choose a plan", "options": $PLAN_OPTIONS}""",
            "\"pro\"",
        ),
        GalleryEntry("radio", """{"key": "plan", "type": "radio", "label": "Plan", "options": $PLAN_OPTIONS}""", "\"pro\""),
        GalleryEntry(
            "radio-inline",
            """{"key": "size", "type": "radio", "label": "Size", "layout": "inline", "options": $SIZES}""",
            "\"m\"",
            listOf(FieldStateKind.FILLED, FieldStateKind.ERROR),
        ),
        GalleryEntry(
            "multiselect",
            """{"key": "tags", "type": "multiselect", "label": "Interests", "options": $PLAN_OPTIONS}""",
            """["free", "team"]""",
        ),
        GalleryEntry(
            "pictures",
            """{"key": "color", "type": "radio", "label": "Colour", "columns": 2, "options": $pictures}""",
            "\"b\"",
            listOf(FieldStateKind.FILLED, FieldStateKind.ERROR, FieldStateKind.DISABLED),
        ),
        GalleryEntry(
            "checkbox",
            """{"key": "agree", "type": "checkbox", "label": "I agree to the terms of service and the privacy policy",
               "helperText": "You can withdraw at any time"}""",
            "true",
        ),
        GalleryEntry(
            "switch",
            """{"key": "news", "type": "switch", "label": "Send me product news", "helperText": "Once a month"}""",
            "true",
        ),
        GalleryEntry("date", """{"key": "d", "type": "date", "label": "Date of birth"}""", "\"1990-05-17\""),
        GalleryEntry("time", """{"key": "t", "type": "time", "label": "Arrival time"}""", "\"14:30\""),
        GalleryEntry("datetime", """{"key": "dt", "type": "datetime", "label": "Pick-up"}""", "\"2026-10-19T09:15\""),
        GalleryEntry(
            "file",
            """{"key": "cv", "type": "file", "label": "CV", "accept": ".pdf", "placeholder": "PDF, up to 5 MB"}""",
            "\"file:///tmp/ada-lovelace-cv.pdf\"",
        ),
        GalleryEntry(
            "scale",
            """{"key": "score", "type": "scale", "label": "How likely are you to recommend us?", "validation": {"min": 0, "max": 10},
               "options": [{"value": "0", "label": "Not at all likely"}, {"value": "10", "label": "Extremely likely"}]}""",
            "9",
        ),
        GalleryEntry(
            "scale-five",
            """{"key": "ease", "type": "scale", "label": "How easy was it?",
               "options": [{"value": "1", "label": "Hard"}, {"value": "3", "label": "Okay"}, {"value": "5", "label": "Easy"}]}""",
            "4",
        ),
        GalleryEntry("rating", """{"key": "stars", "type": "rating", "label": "Rate your stay", "validation": {"max": 5}}""", "4"),
    )

fun stateModel(
    entry: GalleryEntry,
    state: FieldStateKind,
): FormModel {
    val field = requireNotNull(json(entry.field).objectValue).toMutable()
    val key = field["key"]?.stringValue ?: "field"
    if (state == FieldStateKind.ERROR) {
        val validation = field["validation"]?.objectValue?.toMutable() ?: MutableJsonObject()
        validation["required"] = JsonValue.Bool(true)
        field["validation"] = JsonValue.Object(validation)
    }
    val definition = """{"id": "gallery", "title": "Gallery", "submit": {"label": "Send"}, "fields": [${JsonValue.Object(
        field,
    ).toJsonString()}]}"""
    val filled = state == FieldStateKind.FILLED || state == FieldStateKind.DISABLED
    val values = if (filled && entry.value != null) """{"$key": ${entry.value}}""" else "{}"
    val form = model(definition, values)
    when (state) {
        FieldStateKind.ERROR -> form.engine.validate()
        FieldStateKind.DISABLED -> form.loading = true
        else -> Unit
    }
    return form
}

private fun dev.formhaus.core.json.JsonObject.toMutable(): MutableJsonObject =
    MutableJsonObject().also { copy ->
        forEach { (k, v) ->
            copy[k] =
                v
        }
    }
