package dev.formhaus.core.i18n

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.renderer.LabelSource
import dev.formhaus.core.validation.MessageKey
import dev.formhaus.core.validation.MessageSource
import dev.formhaus.core.validation.defaultMessageKeys

@ConsistentCopyVisibility
public data class MissingKeys internal constructor(
    public val messages: List<MessageKey>,
    public val labels: List<String>,
)

public fun missingKeys(
    messages: MessageSource? = null,
    labels: LabelSource? = null,
): MissingKeys =
    MissingKeys(
        messages =
            (messages as? MessageSource.Catalog)
                ?.catalog
                ?.let { catalog ->
                    defaultMessageKeys.filter { !hasText(catalog, it.rawValue) }
                }.orEmpty(),
        labels =
            (labels as? LabelSource.Catalog)
                ?.catalog
                ?.let { catalog ->
                    defaultLabelKeys.filter { !hasText(catalog, it) }
                }.orEmpty(),
    )

internal fun hasText(
    catalog: JsonValue,
    key: String,
): Boolean = catalog[key]?.stringValue?.isNotEmpty() == true
