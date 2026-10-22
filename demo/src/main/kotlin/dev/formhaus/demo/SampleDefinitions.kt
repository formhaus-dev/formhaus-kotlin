package dev.formhaus.demo

object SampleDefinitions {
    val contact =
        """
        {"formatVersion": "1.6", "id": "contact", "title": "Contact us", "submit": {"label": "Send message"},
         "fields": [
           {"key": "name", "type": "text", "label": "Name", "validation": {"required": true}},
           {"key": "email", "type": "email", "label": "Email", "validation": {"required": true}},
           {"key": "topic", "type": "radio", "label": "Topic", "validation": {"required": true},
            "options": [{"value": "sales", "label": "Sales"}, {"value": "support", "label": "Support"}]},
           {"key": "message", "type": "textarea", "label": "Message", "validation": {"required": true, "minLength": 10}}
         ]}
        """.trimIndent()

    val german =
        """
        {"title": "Kontakt", "submit": {"label": "Nachricht senden"},
         "messages": {"required": "Pflichtfeld", "email": "Gib eine gültige E-Mail-Adresse ein", "minLength": "Mindestens {min} Zeichen"},
         "fields": {
           "name": {"label": "Name"},
           "email": {"label": "E-Mail"},
           "topic": {"label": "Thema", "options": {"sales": "Vertrieb", "support": "Support"}},
           "message": {"label": "Nachricht"}
         }}
        """.trimIndent()

    val trip =
        """
        {"formatVersion": "1.6", "id": "trip", "title": "Plan a trip", "submit": {"label": "Request quote"},
         "steps": [
           {"id": "where", "title": "Where", "fields": [
             {"key": "destination", "type": "select", "label": "Destination", "validation": {"required": true},
              "options": [{"value": "lisbon", "label": "Lisbon"}, {"value": "kyoto", "label": "Kyoto"}, {"value": "quebec", "label": "Quebec City"}]},
             {"key": "departure", "type": "date", "label": "Departure", "validation": {"required": true}}
           ]},
           {"id": "who", "title": "Who", "fields": [
             {"key": "travellers", "type": "group", "label": "Travellers", "defaultItems": 1, "validation": {"minLength": 1},
              "fields": [
                {"key": "name", "type": "text", "label": "Full name", "validation": {"required": true}},
                {"key": "age", "type": "number", "label": "Age"}
              ]}
           ]},
           {"id": "extras", "title": "Extras", "fields": [
             {"key": "interests", "type": "multiselect", "label": "Interests", "randomize": true,
              "options": [{"value": "food", "label": "Food"}, {"value": "museums", "label": "Museums"},
                          {"value": "hiking", "label": "Hiking"}, {"value": "none", "label": "None of these", "exclusive": true}]},
             {"key": "notes", "type": "textarea", "label": "Anything else?"}
           ]}
         ]}
        """.trimIndent()
}
