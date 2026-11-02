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
    alerts.announce(label("itemAdded", stringMapOf("item" to JsonValue.String(itemTitle(count, group)))))
    return true
}

public fun FormModel.removeItem(
    index: Int,
    group: String,
): Boolean {
    val name = itemTitle(index, group)
    if (!changeItems(group) { engine.removeItem(group, index) }) return false
    requestFocus(group)
    alerts.announce(label("itemRemoved", stringMapOf("item" to JsonValue.String(name))))
    return true
}

public fun FormModel.moveItem(
    group: String,
    from: Int,
    to: Int,
): Boolean {
    val name = itemTitle(from, group)
    val count = group(group).items.size
    if (!changeItems(group) { engine.moveItem(group, from, to) }) return false
    val up = to < from
    requestFocus(moveKey(group, to, up != (if (up) to == 0 else to == count - 1)))
    val params = stringMapOf("item" to JsonValue.String(name), "position" to JsonValue.Number(to + 1.0))
    alerts.announce(label("itemMoved", params))
    return true
}

internal fun moveKey(
    group: String,
    index: Int,
    up: Boolean,
): String = "$group#${if (up) "up" else "down"}$index"

internal fun FormModel.groupField(key: String): FormField? = structure.visibleFields.firstOrNull { it.key == key && it.isGroup }
