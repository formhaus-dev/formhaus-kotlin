package dev.formhaus.core.renderer

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.messageSource
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FormField
import dev.formhaus.core.validation.MessageKey
import dev.formhaus.core.validation.maxItems
import dev.formhaus.core.validation.messageResolver
import dev.formhaus.core.validation.namedResolver

@RendererApi
public fun FormEngine.maxItemsMessage(field: FormField): String =
    field.validation?.message("maxLengthMessage")
        ?: namedResolver(messageResolver(core.messageSource))(
            MessageKey.MAX_ITEMS,
            stringMapOf("max" to JsonValue.Number(field.maxItems)),
            field,
        )
