package dev.formhaus.core.model

import dev.formhaus.core.definitionvalidation.FormDefinitionError
import dev.formhaus.core.definitionvalidation.definitionErrors
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.jsNumber
import dev.formhaus.core.json.jsString

public class FormStep internal constructor(internal val json: JsonObject, ordinals: Ordinals) : Conditional {
    internal val ordinal: Int = ordinals.take()
    public val id: String = json["id"]?.stringValue ?: ""
    public val fields: List<FormNode> = formNodes(json["fields"], ordinals).orEmpty()
    override val show: List<ShowCondition>? = conditions(json["show"])
    override val showAny: List<ShowCondition>? = conditions(json["showAny"])
    public val routes: List<StepRoute>? =
        json["routes"]?.arrayValue?.mapNotNull { item -> item.objectValue?.let(::StepRoute) }
    public val next: StepButton = StepButton.of(json["next"])
    public val back: StepButton = StepButton.of(json["back"])
    public val skip: StepButton = StepButton.of(json["skip"])

    public val title: String? get() = json["title"]?.stringValue
    public val description: String? get() = json["description"]?.stringValue
    public val canSkip: Boolean get() = json["skip"]?.isTruthy == true

    internal fun button(name: String): StepButton = when (name) {
        "next" -> next
        "back" -> back
        else -> skip
    }
}

public class FormDefinition internal constructor(internal val json: JsonObject) {
    public constructor(json: JsonValue) : this(checkedRoot(json))

    private val ordinals = Ordinals()
    public val id: String = json["id"]?.stringValue ?: ""
    public val submit: FormAction = FormAction(json["submit"]?.objectValue ?: MutableJsonObject())
    public val cancel: FormAction? = json["cancel"]?.objectValue?.let(::FormAction)
    public val fields: List<FormNode>? = formNodes(json["fields"], ordinals)
    public val steps: List<FormStep>? =
        json["steps"]?.arrayValue?.mapNotNull { item -> item.objectValue?.let { FormStep(it, ordinals) } }
    internal val recallsAnswers: Boolean = formatParts(json["formatVersion"]).let { (major, minor) -> major > 1 || minor >= 5 }

    public val title: String? get() = json["title"]?.stringValue
    public val description: String? get() = json["description"]?.stringValue
    public val lang: String? get() = json["lang"]?.stringValue
    public val formatVersion: String? get() = json["formatVersion"]?.stringValue
    public val messages: JsonValue? get() = json["messages"]
    public val labels: JsonValue? get() = json["labels"]
    public val i18n: JsonValue? get() = json["i18n"]
    public val isMultiStep: Boolean get() = !steps.isNullOrEmpty()

    internal val topNodes: List<FormNode>
        get() = if (!steps.isNullOrEmpty()) steps.flatMap { it.fields } else fields.orEmpty()

    internal val allFields: List<FormField> get() = valueFields(topNodes)

    public companion object {
        public const val SUPPORTED_FORMAT_VERSION: String = "1.6"
    }
}

private fun checkedRoot(json: JsonValue): JsonObject {
    val errors = definitionErrors(json)
    val root = json.objectValue
    if (errors.isNotEmpty() || root == null) throw FormDefinitionError(errors)
    return root
}

public fun parseDefinition(json: JsonValue): FormDefinition = FormDefinition(json)

internal fun formatParts(version: JsonValue?): Pair<Double, Double> {
    val text = version?.takeUnless { it.isNull }?.let(::jsString) ?: "1.0"
    val parts = text.split('.').map(::jsNumber)
    return parts[0] to (parts.getOrNull(1) ?: 0.0)
}
