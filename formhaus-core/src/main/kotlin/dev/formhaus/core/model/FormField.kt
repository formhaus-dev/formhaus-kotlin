package dev.formhaus.core.model

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.isInteger

public class FormField internal constructor(override val json: JsonObject, ordinals: Ordinals) : FormNode() {
    override val ordinal: Int = ordinals.take()
    override val key: String = json["key"]?.stringValue ?: ""
    override val type: String = json["type"]?.stringValue ?: ""
    override val show: List<ShowCondition>? = conditions(json["show"])
    override val showAny: List<ShowCondition>? = conditions(json["showAny"])
    public val validation: FieldValidation? = json["validation"]?.objectValue?.let(::FieldValidation)
    public val options: List<FieldOption>? =
        json["options"]?.arrayValue?.mapNotNull { item -> item.objectValue?.let(::FieldOption) }
    internal val optionIndex: OptionIndex? = options?.let(::OptionIndex)
    public val fields: List<FormNode>? = if (type == "group") formNodes(json["fields"], ordinals) else null
    override val children: List<FormNode>? get() = fields

    public val label: String? get() = json["label"]?.stringValue
    public val placeholder: String? get() = json["placeholder"]?.stringValue
    public val helperText: String? get() = json["helperText"]?.stringValue
    public val itemLabel: String? get() = json["itemLabel"]?.stringValue
    public val addLabel: String? get() = json["addLabel"]?.stringValue
    public val removeLabel: String? get() = json["removeLabel"]?.stringValue
    public val layout: JsonValue? get() = json["layout"]
    public val columns: Int? get() = json["columns"]?.numberValue?.let(::exactInt)
    public val accept: JsonValue? get() = json["accept"]
    public val rows: JsonValue? get() = json["rows"]
    public val mask: JsonValue? get() = json["mask"]
    public val inputMode: JsonValue? get() = json["inputMode"]
    public val autocomplete: JsonValue? get() = json["autocomplete"]
    public val autoAdvance: JsonValue? get() = json["autoAdvance"]
    public val props: JsonObject? get() = json["props"]?.objectValue
    public val defaultValue: JsonValue? get() = json["defaultValue"]
    public val source: JsonValue? get() = json["source"]
    public val optionsFrom: JsonValue? get() = json["optionsFrom"]
    public val hasOptionsFrom: Boolean get() = optionsFrom?.isTruthy == true
    public val randomize: Boolean get() = json["randomize"]?.isTruthy == true
    public val defaultItems: Double? get() = json["defaultItems"]?.numberValue
    public val isGroup: Boolean get() = type == "group"
    public val isScale: Boolean get() = type in scaleTypes

    internal val hasPictures: Boolean
        get() = type in pictureTypes && options.orEmpty().any { !it.image.isNullOrEmpty() }

    public val scaleRange: ScaleRange?
        get() {
            if (!isScale) return null
            return ScaleRange(if (type == "rating") 1.0 else validation?.min ?: 1.0, validation?.max ?: 5.0)
        }

    public val optionsDependsOn: List<String>?
        get() = json["optionsDependsOn"]?.arrayValue?.mapNotNull { it.stringValue }

    public val items: List<FormField> get() = fields.orEmpty().filterIsInstance<FormField>()
}

private val pictureTypes = listOf("radio", "multiselect")

private fun exactInt(value: Double): Int? =
    if (value.isInteger && value >= Int.MIN_VALUE && value <= Int.MAX_VALUE) value.toInt() else null
