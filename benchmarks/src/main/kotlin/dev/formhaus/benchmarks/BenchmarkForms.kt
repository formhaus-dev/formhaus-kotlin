package dev.formhaus.benchmarks

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue

object BenchmarkForms {
    val inquiry: String =
        """
        {"formatVersion": "1.6", "id": "inquiry", "title": "Start a project", "submit": {"label": "Send inquiry"},
         "steps": [
           {"id": "project", "title": "Your project", "fields": [
             {"key": "projectType", "type": "radio", "label": "What do you need?", "columns": 2, "validation": {"required": true},
              "options": [
                {"value": "new-site", "label": "New website", "image": "https://example.invalid/new-site.png"},
                {"value": "redesign", "label": "Redesign", "image": "https://example.invalid/redesign.png"},
                {"value": "shop", "label": "Online shop", "image": "https://example.invalid/shop.png"},
                {"value": "landing-page", "label": "Landing page", "image": "https://example.invalid/landing.png"}]},
             {"key": "currentUrl", "type": "text", "label": "Current website", "placeholder": "https://",
              "show": [{"field": "projectType", "eq": "redesign"}], "validation": {"required": true, "pattern": "^https?://\\S+$"}},
             {"key": "pages", "type": "number", "label": "How many pages?", "validation": {"min": 1, "max": 200}},
             {"key": "features", "type": "multiselect", "label": "Features",
              "options": [{"value": "blog", "label": "Blog"}, {"value": "booking", "label": "Booking"},
                          {"value": "payments", "label": "Payments"}, {"value": "languages", "label": "Several languages"}]}
           ]},
           {"id": "budget", "title": "Budget and timing", "fields": [
             {"key": "budget", "type": "select", "label": "Budget", "validation": {"required": true},
              "options": [{"value": "5k", "label": "Under 5,000"}, {"value": "15k", "label": "5,000 to 15,000"},
                          {"value": "50k", "label": "15,000 to 50,000"}, {"value": "more", "label": "More than 50,000"}]},
             {"key": "launch", "type": "date", "label": "Launch date"},
             {"key": "flexible", "type": "switch", "label": "The date is flexible"}
           ]},
           {"id": "contact", "title": "About you", "fields": [
             {"key": "name", "type": "text", "label": "Name", "validation": {"required": true, "minLength": 2}},
             {"key": "email", "type": "email", "label": "Email", "validation": {"required": true}},
             {"key": "phone", "type": "phone", "label": "Phone", "validation": {"pattern": "^\\+?[0-9 ()-]{7,20}$"}},
             {"key": "company", "type": "text", "label": "Company"}
           ]},
           {"id": "details", "title": "Anything else", "fields": [
             {"key": "message", "type": "textarea", "label": "Tell us more, {{name}}", "validation": {"maxLength": 2000}},
             {"key": "satisfaction", "type": "rating", "label": "How did you like this form?", "validation": {"max": 5}},
             {"key": "terms", "type": "checkbox", "label": "I agree to the terms", "validation": {"required": true}}
           ]}
         ]}
        """.trimIndent()

    val inquiryAnswers: String =
        """
        {"projectType": "redesign", "currentUrl": "https://example.com", "pages": 12, "features": ["blog", "payments"],
         "budget": "15k", "launch": "2027-06-14", "flexible": true,
         "name": "Ada Lovelace", "email": "ada@example.com", "phone": "+44 (20) 7946-0958", "company": "Analytical Engines"}
        """.trimIndent()

    val recall: String =
        """
        {"formatVersion": "1.5", "id": "bench", "title": "Bench", "submit": {"label": "Send"},
         "steps": [
           {"id": "about", "title": "About you", "fields": [
             {"key": "name", "type": "text", "label": "Name"},
             {"key": "start", "type": "date", "label": "Start"},
             {"key": "budget", "type": "number", "label": "Budget"},
             {"key": "plan", "type": "multiselect", "label": "Plan",
              "options": [{"value": "pro", "label": "Pro"}, {"value": "team", "label": "Team"}]}
           ]},
           {"id": "review", "title": "Review", "fields": [
             {"key": "plain", "type": "text", "label": "Anything to add?"},
             {"key": "summary", "type": "text", "label": "{{name}}, {{plan}} from {{start}} for {{budget}}"}
           ]}
         ]}
        """.trimIndent()

    const val RECALL_ANSWERS: String = """{"name": "Ada  Lovelace", "start": "2027-06-14", "budget": 12500, "plan": ["pro", "team"]}"""

    fun pictures(count: Int): String {
        val options =
            (0 until count).joinToString {
                """{"value": "v$it", "label": "Label $it", "image": "https://example.invalid/$it.png"}"""
            }
        return form("""{"key": "big", "type": "radio", "label": "Big", "options": [$options]}""")
    }

    fun textFields(count: Int): String =
        form(
            (0 until count).joinToString {
                """{"key": "field$it", "type": "text", "label": "Field $it", "validation": {"required": true, "minLength": 2}}"""
            },
        )

    private fun form(fields: String): String = """{"id": "bench", "title": "Bench", "submit": {"label": "Send"}, "fields": [$fields]}"""
}

fun parseJson(text: String): JsonValue = JsonValue.parse(text)

fun parseObject(text: String): JsonObject = requireNotNull(parseJson(text).objectValue)
