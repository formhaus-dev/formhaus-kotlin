package dev.formhaus.compose.model

import androidx.compose.runtime.snapshots.Snapshot
import dev.formhaus.core.engine.SubmitHandler
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormDefinition

fun fires(
    read: () -> Unit,
    change: () -> Unit,
): Boolean {
    val reads = mutableSetOf<Any>()
    Snapshot.observe(readObserver = { reads += it }) { read() }
    var fired = false
    val handle = Snapshot.registerApplyObserver { changed, _ -> if (changed.any { it in reads }) fired = true }
    try {
        change()
        Snapshot.sendApplyNotifications()
    } finally {
        handle.dispose()
    }
    return fired
}

fun json(text: String): JsonValue = JsonValue.parse(text)

fun values(text: String): JsonObject = requireNotNull(json(text).objectValue)

fun model(
    definition: String,
    values: String = "{}",
    options: FormRendererOptions = FormRendererOptions(),
    onSubmit: SubmitHandler = { _, _ -> null },
): FormModel = FormModel(FormDefinition(json(definition)), values(values), options = options, onSubmit = onSubmit)

val signupDefinition =
    """
    {"id": "signup", "title": "Sign up", "submit": {"label": "Create", "disabled": [{"field": "agree", "neq": true}]},
     "fields": [
       {"key": "email", "type": "email", "label": "Email", "validation": {"required": true}},
       {"key": "name", "type": "text", "label": "Name"},
       {"key": "company", "type": "text", "label": "Company", "show": [{"field": "work", "eq": true}]},
       {"key": "work", "type": "checkbox", "label": "Work account"},
       {"key": "agree", "type": "checkbox", "label": "I agree"}
     ]}
    """.trimIndent()

val stepsDefinition =
    """
    {"id": "trip", "title": "Trip", "submit": {"label": "Book"},
     "steps": [
       {"id": "who", "title": "Who", "fields": [
         {"key": "name", "type": "text", "label": "Name", "validation": {"required": true}},
         {"key": "seat", "type": "radio", "label": "Seat", "autoAdvance": true,
          "options": [{"value": "a", "label": "Aisle"}, {"value": "w", "label": "Window"}]}
       ]},
       {"id": "when", "title": "When", "fields": [{"key": "date", "type": "date", "label": "Date"}]}
     ]}
    """.trimIndent()
