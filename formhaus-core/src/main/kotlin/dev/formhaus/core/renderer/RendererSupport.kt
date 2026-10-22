package dev.formhaus.core.renderer

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.mutableStringMapOf
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.FormStep
import kotlin.math.floor

public fun FieldOption(
    value: String,
    label: String,
    image: String? = null,
): FieldOption {
    val json = mutableStringMapOf<JsonValue>("value" to JsonValue.String(value), "label" to JsonValue.String(label))
    if (image != null) json["image"] = JsonValue.String(image)
    return FieldOption(json)
}

@RendererApi
public val FieldOption.imageKey: Int? get() = image?.hashCode()

@RendererApi
public val FieldOption.safeImage: String? get() = imageSource(image)

@RendererApi
public val FormNode.nodeId: Int get() = ordinal

@RendererApi
public val FormStep.nodeId: Int get() = ordinal

@RendererApi
public val FormField.isRequired: Boolean get() = validation?.required?.isTruthy == true

@RendererApi
public val FormField.advancesOnCommit: Boolean get() = autoAdvance?.isTruthy == true

@RendererApi
public val FormField.hasPictureOptions: Boolean get() = hasPictures

@RendererApi
public val FormDefinition.source: JsonObject get() = json

@ConsistentCopyVisibility
public data class ItemPath internal constructor(
    public val group: String,
    public val index: Int,
    public val field: String,
)

@ConsistentCopyVisibility
public data class RecallDependency internal constructor(
    public val key: String,
    public val isGroup: Boolean,
)

@ConsistentCopyVisibility
public data class RecalledTexts internal constructor(
    public val title: String?,
    public val description: String?,
    public val label: String?,
    public val helperText: String?,
    public val placeholder: String?,
) {
    internal constructor(json: JsonObject) : this(
        json["title"]?.stringValue,
        json["description"]?.stringValue,
        json["label"]?.stringValue,
        json["helperText"]?.stringValue,
        json["placeholder"]?.stringValue,
    )
}

@RendererApi
public fun clampedInt(
    value: Double?,
    range: IntRange,
): Int? {
    if (value == null || !value.isFinite()) return null
    val down = floor(value)
    return when {
        down <= range.first -> range.first
        down >= range.last -> range.last
        else -> down.toInt()
    }
}
