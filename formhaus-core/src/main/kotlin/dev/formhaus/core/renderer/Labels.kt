package dev.formhaus.core.renderer

import dev.formhaus.core.i18n.defaultLabels
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.validation.formatText
import dev.formhaus.core.validation.noParams

public typealias LabelFn = (String, JsonObject) -> String?

public sealed interface LabelSource {
    public data class Catalog(public val catalog: JsonValue) : LabelSource

    public class Function(public val resolve: LabelFn) : LabelSource
}

internal fun labelFor(key: String, params: JsonObject = noParams, vararg sources: LabelSource?): String {
    for (source in sources) {
        when (source) {
            is LabelSource.Function -> source.resolve(key, params)?.takeIf { it.isNotEmpty() }?.let { return it }
            is LabelSource.Catalog -> source.catalog[key]?.stringValue?.takeIf { it.isNotEmpty() }?.let { return formatText(it, params) }
            null -> continue
        }
    }
    return formatText(defaultLabels[key].orEmpty(), params)
}

@RendererApi
public fun formatTemplate(template: String, params: JsonObject): String = formatText(template, params)
