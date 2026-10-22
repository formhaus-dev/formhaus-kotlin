package dev.formhaus.core.renderer

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.itemLimits
import dev.formhaus.core.model.FormField
import dev.formhaus.core.validation.MAX_GROUP_ITEMS

@ConsistentCopyVisibility
public data class ItemBounds internal constructor(
    public val min: Int,
    public val max: Int,
)

@RendererApi
public fun FormEngine.itemLimits(group: FormField): ItemBounds {
    val limits = group.itemLimits
    return ItemBounds(
        clampedInt(limits.min, 0..1_000_000) ?: 0,
        clampedInt(limits.max, 0..MAX_GROUP_ITEMS) ?: MAX_GROUP_ITEMS,
    )
}
