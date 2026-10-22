package dev.formhaus.compose.model

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FormField
import dev.formhaus.core.renderer.itemTitle
import dev.formhaus.core.renderer.itemTitleDependencies

public fun FormModel.itemTitle(
    index: Int,
    group: String,
): String {
    val field = groupField(group) ?: return ""
    track(engine.itemTitleDependencies(field, index))
    return engine.itemTitle(field, index, options.labels)
}

public fun FormModel.addItem(group: String): Boolean {
    val count = group(group).items.size
    if (!changeItems(group) { engine.addItem(group) }) return false
    group(group)
        .items
        .lastOrNull()
        ?.fields
        ?.firstOrNull { it.type != "hidden" }
        ?.let { requestFocus("$group.$count.${it.key}") }
    announce(label("itemAdded", stringMapOf("item" to JsonValue.String(itemTitle(count, group)))))
    return true
}

public fun FormModel.removeItem(
    index: Int,
    group: String,
): Boolean {
    val name = itemTitle(index, group)
    if (!changeItems(group) { engine.removeItem(group, index) }) return false
    requestFocus(group)
    announce(label("itemRemoved", stringMapOf("item" to JsonValue.String(name))))
    return true
}

internal fun FormModel.groupField(key: String): FormField? = structure.visibleFields.firstOrNull { it.key == key && it.isGroup }
