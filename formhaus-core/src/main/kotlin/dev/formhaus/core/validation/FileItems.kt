package dev.formhaus.core.renderer

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.visibility.isBlank

@RendererApi
public fun fileItems(value: JsonValue?): List<JsonValue> = value?.arrayValue ?: listOfNotNull(value?.takeUnless(::isBlank))
