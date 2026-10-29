package dev.formhaus.core.renderer

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.messageSource
import dev.formhaus.core.model.FormField
import dev.formhaus.core.validation.fileListMax
import dev.formhaus.core.validation.maxFilesMessage
import dev.formhaus.core.validation.messageResolver
import dev.formhaus.core.validation.namedResolver

@RendererApi
public fun FormEngine.maxItemsMessage(field: FormField): String {
    val resolve = namedResolver(messageResolver(core.messageSource))
    return field.maxFilesMessage { key, params -> resolve(key, params, field) }
}

@RendererApi
public fun fileListLimit(field: FormField): Int = field.fileListMax.toInt()
