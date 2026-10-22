package dev.formhaus.compose.gallery

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import dev.formhaus.compose.fields.CheckboxField
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.form.FormBehavior
import dev.formhaus.compose.form.FormComponents
import dev.formhaus.compose.form.Slot
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.back
import dev.formhaus.compose.model.model
import dev.formhaus.compose.model.next
import dev.formhaus.compose.model.submit
import dev.formhaus.core.engine.SubmitResult
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf

private val trip =
    """
    {"id": "trip", "title": "Trip", "submit": {"label": "Book trip"}, "cancel": {"label": "Cancel"},
     "steps": [
       {"id": "who", "title": "Who is travelling?", "description": "We send the tickets to the first traveller.",
        "skip": {"label": "Skip for now"}, "fields": [
         {"key": "travellers", "type": "group", "label": "Travellers", "itemLabel": "Traveller {index}", "addLabel": "Add traveller",
          "defaultItems": 1, "validation": {"required": true, "maxLength": 2}, "fields": [
            {"key": "name", "type": "text", "label": "Full name", "validation": {"required": true}},
            {"key": "email", "type": "email", "label": "Email"}]},
         {"key": "notes", "type": "textarea", "label": "Anything we should know?"}]},
       {"id": "seat", "title": "Pick a seat", "fields": [
         {"key": "seat", "type": "radio", "label": "Seat", "options": [{"value": "a", "label": "Aisle"}, {"value": "w", "label": "Window"}]}]},
       {"id": "done", "title": "Review", "fields": [{"key": "agree", "type": "checkbox", "label": "The details are correct"}]}
     ]}
    """.trimIndent()

private val contact =
    """
    {"id": "contact", "title": "Contact", "submit": {"label": "Send message"}, "fields": [
      {"key": "about", "type": "section", "title": "About you", "description": "So we know who to answer.", "fields": [
        {"key": "name", "type": "text", "label": "Name", "validation": {"required": true}},
        {"key": "email", "type": "email", "label": "Email", "validation": {"required": true}}]},
      {"key": "topic", "type": "select", "label": "Topic", "options": [{"value": "sales", "label": "Sales"}, {"value": "help", "label": "Help"}]},
      {"key": "message", "type": "textarea", "label": "Message", "validation": {"required": true, "minLength": 10}}]}
    """.trimIndent()

private const val ONE_TRAVELLER = """{"travellers": [{"name": "Ada Lovelace", "email": "ada@example.com"}]}"""
private const val TWO_TRAVELLERS =
    """{"travellers": [{"name": "Ada Lovelace", "email": "ada@example.com"}, {"name": "Charles Babbage"}], "seat": "w"}"""
private const val FILLED_CONTACT = """{"name": "Ada", "email": "ada@example.com", "message": "Please call me back tomorrow."}"""

private val plainText: Slot<FieldContext> = { context ->
    OutlinedTextField(context.text, { context.setValue(JsonValue.String(it)) }, Modifier.fillMaxWidth(), label = { Text(context.label) })
}

private val headless: FormComponents =
    FormComponents.headless
        .field("text", plainText)
        .field("email", plainText)
        .field("checkbox") { CheckboxField(it) }

class FormEntry(
    val name: String,
    val components: FormComponents = FormComponents.standard,
    val behavior: FormBehavior = FormBehavior(errorSummary = true, stepErrorMarks = true),
    val prepare: suspend FormModel.() -> Unit = {},
    val build: () -> FormModel,
) {
    override fun toString(): String = name
}

private fun rejecting(): FormModel =
    model(contact, FILLED_CONTACT) { _, _ ->
        SubmitResult.Rejected(stringMapOf("email" to "This address is already registered"), listOf("We could not send your message."))
    }

val formEntries: List<FormEntry> =
    listOf(
        FormEntry("form-step-one") { model(trip) },
        FormEntry("form-step-two", prepare = { next() }) { model(trip, ONE_TRAVELLER) },
        FormEntry("form-step-three", prepare = {
            next()
            next()
        }) { model(trip, TWO_TRAVELLERS) },
        FormEntry("form-step-invalid", prepare = { next() }) { model(trip) },
        FormEntry("form-group-limit", prepare = {
            next()
            back()
        }) { model(trip, TWO_TRAVELLERS) },
        FormEntry("form-section") { model(contact) },
        FormEntry("form-errors", prepare = { submit() }) { model(contact, """{"message": "Hi"}""") },
        FormEntry("form-rejected", prepare = { submit() }, build = ::rejecting),
        FormEntry("system-step-one", FormComponents.system) { model(trip, ONE_TRAVELLER) },
        FormEntry("system-errors", FormComponents.system, prepare = { submit() }) { model(contact, """{"message": "Hi"}""") },
        FormEntry("headless", headless) { model(contact.replace("textarea", "text"), """{"name": "Ada"}""") },
    )
