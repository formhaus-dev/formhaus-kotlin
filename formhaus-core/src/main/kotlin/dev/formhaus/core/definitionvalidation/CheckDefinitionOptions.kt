package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormDefinition

public class CheckDefinitionOptions(
    public val validators: List<String>? = null,
)

internal fun definitionIssues(
    input: JsonValue,
    warnings: Boolean,
    validators: List<String>? = null,
): List<DefinitionIssue> {
    val shape = shapeIssues(input)
    val root = input.objectValue
    if (shape.isNotEmpty() || root == null) return shape
    val context = CheckContext(FormDefinition(root), validators?.toSet())
    return metaIssues(root) + structureIssues(context) + conditionIssues(context) + fieldIssues(context) +
        sourceIssues(context) +
        graphIssues(context) + if (warnings) definitionWarnings(context) else emptyList()
}

public fun checkDefinition(
    input: JsonValue,
    options: CheckDefinitionOptions = CheckDefinitionOptions(),
): List<DefinitionIssue> = definitionIssues(input, warnings = true, validators = options.validators)

internal fun definitionErrors(input: JsonValue): List<DefinitionIssue> =
    definitionIssues(input, warnings = false).filter {
        it.isError
    }
