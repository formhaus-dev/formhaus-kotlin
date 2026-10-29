package dev.formhaus.core.validation

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.visibility.isBlank

public fun fileItems(value: JsonValue?): List<JsonValue> = value?.arrayValue ?: listOfNotNull(value?.takeUnless(::isBlank))
