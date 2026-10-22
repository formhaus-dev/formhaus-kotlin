package dev.formhaus.core.model

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue

internal val conditionOperators =
    listOf("eq", "neq", "in", "notIn", "contains", "notContains", "notEmpty", "gt", "gte", "lt", "lte")

@ConsistentCopyVisibility
public data class ShowCondition internal constructor(internal val json: JsonObject) {
    public val field: String? get() = json["field"]?.stringValue

    public val operatorName: String? get() = conditionOperators.firstOrNull { it in json }

    public fun operand(name: String): JsonValue? = json[name]
}

public interface Conditional {
    public val show: List<ShowCondition>?
    public val showAny: List<ShowCondition>?
}

internal val Conditional.conditionFields: List<String>
    get() = (show.orEmpty() + showAny.orEmpty()).mapNotNull { it.field }

internal val Conditional.isConditioned: Boolean get() = show != null || showAny != null

internal fun conditions(value: JsonValue?): List<ShowCondition>? =
    value?.arrayValue?.mapNotNull { item -> item.objectValue?.let(::ShowCondition) }

@ConsistentCopyVisibility
public data class FormAction internal constructor(internal val json: JsonObject) {
    public val label: String? get() = json["label"]?.stringValue
    public val variant: String? get() = json["variant"]?.stringValue
    public val disabled: List<ShowCondition>? get() = conditions(json["disabled"])
}

@ConsistentCopyVisibility
public data class StepRoute internal constructor(internal val json: JsonObject) : Conditional {
    public val to: String? get() = json["to"]?.stringValue
    public val endsForm: Boolean get() = json["to"]?.isNull == true
    override val show: List<ShowCondition>? get() = conditions(json["show"])
    override val showAny: List<ShowCondition>? get() = conditions(json["showAny"])
}

public sealed class StepButton {
    public data object Absent : StepButton()

    public data object Hidden : StepButton()

    public data class Action(override val action: FormAction) : StepButton()

    public open val action: FormAction? get() = null

    internal companion object {
        fun of(value: JsonValue?): StepButton = when {
            value == JsonValue.Bool(false) -> Hidden
            value is JsonValue.Object -> Action(FormAction(value.members))
            else -> Absent
        }
    }
}
