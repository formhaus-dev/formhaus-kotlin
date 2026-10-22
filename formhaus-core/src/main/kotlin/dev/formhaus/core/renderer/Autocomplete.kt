package dev.formhaus.core.renderer

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.json.jsTrimmed
import dev.formhaus.core.model.FormField
import dev.formhaus.core.unicode.jsLowercase

private fun sectionName(key: String): String =
    buildString {
        for (unit in key) {
            if (unit in 'a'..'z' || unit in '0'..'9') append(unit) else append("_${unit.code.toString(16)}_")
        }
    }

@RendererApi
public fun FormEngine.autocomplete(
    field: FormField,
    at: String? = null,
): String? {
    val written = field.autocomplete?.stringValue ?: return null
    val token = written.jsTrimmed
    val lower = jsLowercase(token)
    val parts = at?.let(core.visibility::parse)
    if (parts == null || token.isEmpty() || lower == "on" || lower == "off") return written
    val section = "section-${sectionName(parts.group)}-${parts.index}"
    return if (lower.startsWith("section-")) "$section-${token.substring(8)}" else "$section $token"
}
