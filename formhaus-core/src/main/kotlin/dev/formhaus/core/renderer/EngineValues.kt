package dev.formhaus.core.renderer

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.optionName
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.visibility.itemsOf

@RendererApi
public fun FormEngine.optionKey(key: String): String = optionName(key)

@RendererApi
public fun FormEngine.itemPath(key: String): ItemPath? = core.visibility.parse(key)?.let { ItemPath(it.group, it.index, it.field) }

@RendererApi
public fun FormEngine.value(key: String): JsonValue? {
    val path = itemPath(key) ?: return core.values[key]
    return itemsOf(core.values[path.group]).getOrNull(path.index)?.objectValue?.get(path.field)
}

@RendererApi
public fun FormEngine.hasValue(key: String): Boolean = value(key).let { it != null && it != JsonValue.String("") }
