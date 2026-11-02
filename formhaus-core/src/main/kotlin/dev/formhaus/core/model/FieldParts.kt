package dev.formhaus.core.model

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue

@ConsistentCopyVisibility
public data class FieldOption internal constructor(
    internal val json: JsonObject,
    internal val rules: Boolean = true,
) {
    public val value: String? get() = json["value"]?.stringValue
    public val label: String? get() = json["label"]?.stringValue
    public val image: String? get() = json["image"]?.stringValue
    public val exclusive: Boolean get() = json["exclusive"]?.isTruthy == true
    public val pinned: Boolean get() = json["pinned"]?.isTruthy == true
    public val description: String? get() = if (rules) json["description"]?.stringValue else null
    public val disabled: ConditionRule? = if (rules) ConditionRule.of(json["disabled"]) else null
}

@ConsistentCopyVisibility
public data class FieldValidation internal constructor(
    internal val json: JsonObject,
) {
    public val required: JsonValue? get() = json["required"]
    public val minLength: Double? get() = json["minLength"]?.numberValue
    public val maxLength: Double? get() = json["maxLength"]?.numberValue
    public val min: Double? get() = json["min"]?.numberValue
    public val max: Double? get() = json["max"]?.numberValue
    public val pattern: String? get() = json["pattern"]?.stringValue
    public val matchField: String? get() = json["matchField"]?.stringValue
    public val validator: String? get() = json["validator"]?.stringValue

    internal fun message(name: String): String? = json[name]?.stringValue
}

public data class ScaleRange(
    public val min: Double,
    public val max: Double,
)

internal val scaleTypes = listOf("scale", "rating")
