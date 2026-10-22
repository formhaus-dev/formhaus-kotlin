package dev.formhaus.core.i18n

import dev.formhaus.core.definitionvalidation.FormDefinitionError
import dev.formhaus.core.engine.templateTokens
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.isBlankText
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.validation.formatText

public class Localized internal constructor(
    public val definition: FormDefinition,
    public val messages: JsonValue,
    public val labels: JsonValue,
)

internal fun lookup(
    overlay: JsonValue?,
    path: List<String>,
): String? {
    var node = overlay
    for (key in path) node = node?.objectValue?.get(key)
    return node?.stringValue?.takeUnless { it.isBlankText }
}

public fun localize(
    input: JsonValue,
    overlay: JsonValue,
    lang: String? = null,
): Localized {
    val parsed = FormDefinition(input)
    val errors = translationErrors(parsed)
    if (errors.isNotEmpty()) throw FormDefinitionError(errors)
    return applyOverlay(parsed, overlay, lang)
}

internal fun applyOverlay(
    parsed: FormDefinition,
    overlay: JsonValue,
    lang: String? = null,
): Localized {
    val source = JsonValue.Object(parsed.json)
    val patch = JsonPatch()
    if (lang != null) patch.set(emptyList(), "lang", JsonValue.String(lang))
    for (slot in definitionSlots(parsed)) {
        val text = lookup(overlay, slot.address) ?: continue
        val value = JsonValue.String(if (slot.kind == SlotKind.RULE) formatText(text, slot.params) else text)
        val catalog = if (slot.kind == SlotKind.MESSAGE || slot.kind == SlotKind.LABEL) slot.location.first() else null
        if (catalog != null &&
            source[catalog]?.objectValue == null
        ) {
            patch.set(emptyList(), catalog, JsonValue.Object(MutableJsonObject()))
        }
        patch.set(slot.location, slot.property, value)
    }
    val json = patch.applied(source)
    val empty = JsonValue.Object(MutableJsonObject())
    return Localized(
        FormDefinition(json.objectValue ?: parsed.json),
        json["messages"] ?: empty,
        json["labels"] ?: empty,
    )
}

internal fun placeholders(text: String): List<String> = templateTokens(text).distinct().sorted()
