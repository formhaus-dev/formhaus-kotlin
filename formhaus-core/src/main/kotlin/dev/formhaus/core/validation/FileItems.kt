package dev.formhaus.core.validation

import dev.formhaus.core.json.JsonValue

public fun fileItems(value: JsonValue?): List<JsonValue> =
    (value?.arrayValue ?: listOfNotNull(value)).filterNot { it.isNull || it.stringValue == "" }
