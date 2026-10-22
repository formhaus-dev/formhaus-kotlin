package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue

private const val MAX_NESTED_FIELD_LISTS = 3
private const val MAX_COLUMNS = 4.0

internal fun nestable(path: String): Boolean = path.split("/fields/").size <= MAX_NESTED_FIELD_LISTS

private val conditionRules =
    listOf(
        "field" to ShapeKind.STRING,
        "eq" to ShapeKind.SCALAR,
        "neq" to ShapeKind.SCALAR,
        "in" to ShapeKind.LIST,
        "notIn" to ShapeKind.LIST,
        "contains" to ShapeKind.SCALAR,
        "notContains" to ShapeKind.SCALAR,
        "notEmpty" to ShapeKind.BOOLEAN,
        "gt" to ShapeKind.NUMBER,
        "gte" to ShapeKind.NUMBER,
        "lt" to ShapeKind.NUMBER,
        "lte" to ShapeKind.NUMBER,
    )

private fun condition(
    value: JsonObject,
    path: String,
) = typedProperties(value, conditionRules, path)

private fun conditionLists(
    value: JsonObject,
    path: String,
): List<DefinitionIssue> =
    objectList(value["show"], "$path/show", "a condition object", ::condition) +
        objectList(value["showAny"], "$path/showAny", "a condition object", ::condition)

private fun action(
    value: JsonValue?,
    path: String,
): List<DefinitionIssue> {
    val json = value?.objectValue ?: return emptyList()
    return typedProperties(json, listOf("label" to ShapeKind.STRING), path) +
        objectList(json["disabled"], "$path/disabled", "a condition object", ::condition)
}

private val optionRules =
    listOf(
        "label" to ShapeKind.STRING,
        "image" to ShapeKind.STRING,
        "exclusive" to ShapeKind.BOOLEAN,
        "pinned" to ShapeKind.BOOLEAN,
    )

private fun option(
    value: JsonObject,
    path: String,
) = requiredString(value, "value", path) + typedProperties(value, optionRules, path)

private val validationRules =
    listOf(
        "required" to ShapeKind.REQUIREMENT,
        "minLength" to ShapeKind.NUMBER,
        "maxLength" to ShapeKind.NUMBER,
        "min" to ShapeKind.NUMBER,
        "max" to ShapeKind.NUMBER,
        "pattern" to ShapeKind.STRING,
        "matchField" to ShapeKind.STRING,
        "validator" to ShapeKind.STRING,
        "minLengthMessage" to ShapeKind.STRING,
        "maxLengthMessage" to ShapeKind.STRING,
        "minMessage" to ShapeKind.STRING,
        "maxMessage" to ShapeKind.STRING,
        "patternMessage" to ShapeKind.STRING,
        "matchFieldMessage" to ShapeKind.STRING,
    )

private fun validation(
    value: JsonValue?,
    path: String,
): List<DefinitionIssue> {
    value ?: return emptyList()
    val json =
        value.objectValue ?: return listOf(error(IssueCode.INVALID_TYPE, path, "must be an object of validation rules"))
    return typedProperties(json, validationRules, path)
}

private fun props(
    value: JsonValue?,
    path: String,
): List<DefinitionIssue> =
    if (value == null ||
        value.objectValue != null
    ) {
        emptyList()
    } else {
        listOf(error(IssueCode.INVALID_TYPE, path, "must be an object"))
    }

private fun dependencies(
    value: JsonValue?,
    path: String,
): List<DefinitionIssue> {
    val valid = value == null || value.arrayValue?.all { it.stringValue != null } == true
    return if (valid) emptyList() else listOf(error(IssueCode.INVALID_TYPE, path, "must be an array of field keys"))
}

private fun columns(
    value: JsonValue?,
    path: String,
): List<DefinitionIssue> {
    if (value == null || (value.isInteger && (value.numberValue ?: 0.0) in 1.0..MAX_COLUMNS)) return emptyList()
    val code = if (value.numberValue == null) IssueCode.INVALID_TYPE else IssueCode.INVALID_COLUMNS
    return listOf(error(code, path, "\"columns\" must be a whole number from 1 to 4"))
}

private val labelRules =
    listOf(
        "label" to ShapeKind.STRING,
        "placeholder" to ShapeKind.STRING,
        "autocomplete" to ShapeKind.STRING,
        "helperText" to ShapeKind.STRING,
        "title" to ShapeKind.STRING,
        "description" to ShapeKind.STRING,
        "itemLabel" to ShapeKind.STRING,
        "addLabel" to ShapeKind.STRING,
        "removeLabel" to ShapeKind.STRING,
        "defaultItems" to ShapeKind.NUMBER,
        "randomize" to ShapeKind.BOOLEAN,
    )

private fun field(
    value: JsonObject,
    path: String,
): List<DefinitionIssue> {
    val type = value["type"]?.stringValue
    val container = (type == "section" || type == "group") && nestable(path)
    return requiredString(value, "key", path) + requiredString(value, "type", path) + typedProperties(value, labelRules, path) +
        (
            if (container) {
                objectList(
                    value["fields"],
                    "$path/fields",
                    "a field object",
                    ::field,
                    required = true,
                )
            } else {
                emptyList()
            }
        ) +
        conditionLists(value, path) +
        objectList(value["options"], "$path/options", "an option object", ::option) +
        validation(value["validation"], "$path/validation") +
        dependencies(value["optionsDependsOn"], "$path/optionsDependsOn") +
        props(value["props"], "$path/props") +
        columns(value["columns"], "$path/columns")
}

private fun route(
    value: JsonObject,
    path: String,
): List<DefinitionIssue> {
    val target =
        when (value["to"]) {
            null -> listOf(missing(path, "to"))
            JsonValue.Null, is JsonValue.String -> emptyList()
            else -> listOf(error(IssueCode.INVALID_TYPE, "$path/to", "\"to\" must be a step id or null"))
        }
    return target + conditionLists(value, path)
}

private fun step(
    value: JsonObject,
    path: String,
): List<DefinitionIssue> =
    requiredString(
        value,
        "id",
        path,
    ) + typedProperties(value, listOf("title" to ShapeKind.STRING, "description" to ShapeKind.STRING), path) +
        objectList(value["fields"], "$path/fields", "a field object", ::field, required = true) +
        conditionLists(value, path) +
        objectList(value["routes"], "$path/routes", "a route object", ::route) +
        listOf("next", "back", "skip").flatMap { action(value[it], "$path/$it") }

internal fun shapeIssues(input: JsonValue): List<DefinitionIssue> {
    val json =
        input.objectValue ?: return listOf(error(IssueCode.INVALID_TYPE, "", "the form definition must be an object"))
    val format = formatIssues(json)
    if (format.isNotEmpty()) return format
    return objectList(json["fields"], "/fields", "a field object", ::field) +
        objectList(json["steps"], "/steps", "a step object", ::step) +
        action(json["submit"], "/submit") + action(json["cancel"], "/cancel")
}

private const val SUBMIT_HINT = "Add \"submit\": { \"label\": \"Submit\" }"

internal fun metaIssues(input: JsonObject): List<DefinitionIssue> {
    val submit =
        when (input["submit"]) {
            null -> listOf(missing("", "submit", SUBMIT_HINT))
            is JsonValue.Object -> emptyList()
            else -> listOf(error(IssueCode.INVALID_TYPE, "/submit", "must be an action object", SUBMIT_HINT))
        }
    return requiredString(input, "id", "") + submit
}
