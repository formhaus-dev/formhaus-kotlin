package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.regex.JsRegex

private val versionPattern = JsRegex.unchecked("^[1-9]\\d*(\\.\\d+)?$")
private const val MAX_NESTED_FIELD_LISTS = 3
private const val MAX_COLUMNS = 4.0

internal fun nestable(path: String): Boolean = path.split("/fields/").size <= MAX_NESTED_FIELD_LISTS

private enum class Kind(val label: String, val accepts: (JsonValue) -> Boolean) {
    STRING("a string", { it.stringValue != null }),
    NUMBER("a number", { it.isFiniteNumber }),
    BOOLEAN("true or false", { it.boolValue != null }),
    REQUIREMENT("true, false or a message string", { it.boolValue != null || it.stringValue != null }),
    SCALAR("a string, number or boolean", { it.typeName in listOf("string", "number", "boolean") }),
    LIST("an array of strings or numbers", { value -> value.arrayValue?.all { it.stringValue != null || it.numberValue != null } == true }),
}

private typealias Check = (JsonObject, String) -> List<DefinitionIssue>

private fun typed(value: JsonObject, rules: List<Pair<String, Kind>>, path: String): List<DefinitionIssue> = rules.mapNotNull { (key, kind) ->
    val item = value[key]
    if (item == null || kind.accepts(item)) null else error(IssueCode.INVALID_TYPE, "$path/${pointer(key)}", "\"$key\" must be ${kind.label}")
}

internal fun missing(path: String, key: String, hint: String? = null, severity: DefinitionIssue.Severity = DefinitionIssue.Severity.ERROR) =
    error(IssueCode.MISSING_PROPERTY, path, "missing required property \"$key\"", hint).withSeverity(severity)

private fun list(value: JsonValue?, path: String, name: String, check: Check, required: Boolean = false): List<DefinitionIssue> {
    if (!required && (value == null || value.isNull)) return emptyList()
    if (value == null) {
        val key = path.substringAfterLast('/')
        return listOf(missing(path.dropLast(key.length + 1), key))
    }
    val items = value.arrayValue
        ?: return listOf(error(IssueCode.INVALID_TYPE, path, "must be an array", if (required) "Use [] when there is nothing to list" else null))
    return items.withIndex().flatMap { (index, item) ->
        val json = item.objectValue ?: return@flatMap listOf(error(IssueCode.INVALID_TYPE, "$path/$index", "must be $name"))
        check(json, "$path/$index")
    }
}

private fun text(value: JsonObject, key: String, path: String): List<DefinitionIssue> {
    val item = value[key] ?: return listOf(missing(path, key))
    return if (item.stringValue != null) emptyList() else listOf(error(IssueCode.INVALID_TYPE, "$path/${pointer(key)}", "\"$key\" must be a string"))
}

private val conditionRules = listOf(
    "field" to Kind.STRING, "eq" to Kind.SCALAR, "neq" to Kind.SCALAR, "in" to Kind.LIST, "notIn" to Kind.LIST,
    "contains" to Kind.SCALAR, "notContains" to Kind.SCALAR, "notEmpty" to Kind.BOOLEAN, "gt" to Kind.NUMBER,
    "gte" to Kind.NUMBER, "lt" to Kind.NUMBER, "lte" to Kind.NUMBER,
)

private fun condition(value: JsonObject, path: String) = typed(value, conditionRules, path)

private fun conditionLists(value: JsonObject, path: String): List<DefinitionIssue> =
    list(value["show"], "$path/show", "a condition object", ::condition) + list(value["showAny"], "$path/showAny", "a condition object", ::condition)

private fun action(value: JsonValue?, path: String): List<DefinitionIssue> {
    val json = value?.objectValue ?: return emptyList()
    return typed(json, listOf("label" to Kind.STRING), path) + list(json["disabled"], "$path/disabled", "a condition object", ::condition)
}

private val optionRules = listOf("label" to Kind.STRING, "image" to Kind.STRING, "exclusive" to Kind.BOOLEAN, "pinned" to Kind.BOOLEAN)

private fun option(value: JsonObject, path: String) = text(value, "value", path) + typed(value, optionRules, path)

private val validationRules = listOf(
    "required" to Kind.REQUIREMENT, "minLength" to Kind.NUMBER, "maxLength" to Kind.NUMBER, "min" to Kind.NUMBER, "max" to Kind.NUMBER,
    "pattern" to Kind.STRING, "matchField" to Kind.STRING, "validator" to Kind.STRING, "minLengthMessage" to Kind.STRING,
    "maxLengthMessage" to Kind.STRING, "minMessage" to Kind.STRING, "maxMessage" to Kind.STRING, "patternMessage" to Kind.STRING,
    "matchFieldMessage" to Kind.STRING,
)

private fun validation(value: JsonValue?, path: String): List<DefinitionIssue> {
    value ?: return emptyList()
    val json = value.objectValue ?: return listOf(error(IssueCode.INVALID_TYPE, path, "must be an object of validation rules"))
    return typed(json, validationRules, path)
}

private fun props(value: JsonValue?, path: String): List<DefinitionIssue> =
    if (value == null || value.objectValue != null) emptyList() else listOf(error(IssueCode.INVALID_TYPE, path, "must be an object"))

private fun dependencies(value: JsonValue?, path: String): List<DefinitionIssue> {
    val valid = value == null || value.arrayValue?.all { it.stringValue != null } == true
    return if (valid) emptyList() else listOf(error(IssueCode.INVALID_TYPE, path, "must be an array of field keys"))
}

private fun columns(value: JsonValue?, path: String): List<DefinitionIssue> {
    if (value == null || (value.isInteger && (value.numberValue ?: 0.0) in 1.0..MAX_COLUMNS)) return emptyList()
    val code = if (value.numberValue == null) IssueCode.INVALID_TYPE else IssueCode.INVALID_COLUMNS
    return listOf(error(code, path, "\"columns\" must be a whole number from 1 to 4"))
}

private val labelRules = listOf(
    "label" to Kind.STRING, "placeholder" to Kind.STRING, "autocomplete" to Kind.STRING, "helperText" to Kind.STRING,
    "title" to Kind.STRING, "description" to Kind.STRING, "itemLabel" to Kind.STRING, "addLabel" to Kind.STRING,
    "removeLabel" to Kind.STRING, "defaultItems" to Kind.NUMBER, "randomize" to Kind.BOOLEAN,
)

private fun field(value: JsonObject, path: String): List<DefinitionIssue> {
    val type = value["type"]?.stringValue
    val container = (type == "section" || type == "group") && nestable(path)
    return text(value, "key", path) + text(value, "type", path) + typed(value, labelRules, path) +
        (if (container) list(value["fields"], "$path/fields", "a field object", ::field, required = true) else emptyList()) +
        conditionLists(value, path) +
        list(value["options"], "$path/options", "an option object", ::option) +
        validation(value["validation"], "$path/validation") +
        dependencies(value["optionsDependsOn"], "$path/optionsDependsOn") +
        props(value["props"], "$path/props") +
        columns(value["columns"], "$path/columns")
}

private fun route(value: JsonObject, path: String): List<DefinitionIssue> {
    val target = when (value["to"]) {
        null -> listOf(missing(path, "to"))
        JsonValue.Null, is JsonValue.String -> emptyList()
        else -> listOf(error(IssueCode.INVALID_TYPE, "$path/to", "\"to\" must be a step id or null"))
    }
    return target + conditionLists(value, path)
}

private fun step(value: JsonObject, path: String): List<DefinitionIssue> =
    text(value, "id", path) + typed(value, listOf("title" to Kind.STRING, "description" to Kind.STRING), path) +
        list(value["fields"], "$path/fields", "a field object", ::field, required = true) +
        conditionLists(value, path) +
        list(value["routes"], "$path/routes", "a route object", ::route) +
        listOf("next", "back", "skip").flatMap { action(value[it], "$path/$it") }

private fun majorVersion(version: String): Double = version.substringBefore('.').toDoubleOrNull() ?: 0.0

internal fun formatIssues(input: JsonObject): List<DefinitionIssue> {
    val version = input["formatVersion"] ?: return emptyList()
    val text = version.stringValue
    if (text == null || versionPattern.test(text) != true) {
        val hint = "Use \"${FormDefinition.SUPPORTED_FORMAT_VERSION}\""
        return listOf(error(IssueCode.INVALID_TYPE, "/formatVersion", "must be a version string", hint))
    }
    if (majorVersion(text) <= majorVersion(FormDefinition.SUPPORTED_FORMAT_VERSION)) return emptyList()
    return listOf(error(IssueCode.UNSUPPORTED_FORMAT_VERSION, "/formatVersion", "format $text is not supported", "Upgrade @formhaus/core"))
}

internal fun shapeIssues(input: JsonValue): List<DefinitionIssue> {
    val json = input.objectValue ?: return listOf(error(IssueCode.INVALID_TYPE, "", "the form definition must be an object"))
    val format = formatIssues(json)
    if (format.isNotEmpty()) return format
    return list(json["fields"], "/fields", "a field object", ::field) + list(json["steps"], "/steps", "a step object", ::step) +
        action(json["submit"], "/submit") + action(json["cancel"], "/cancel")
}

private const val SUBMIT_HINT = "Add \"submit\": { \"label\": \"Submit\" }"

internal fun metaIssues(input: JsonObject): List<DefinitionIssue> {
    val submit = when (input["submit"]) {
        null -> listOf(missing("", "submit", SUBMIT_HINT))
        is JsonValue.Object -> emptyList()
        else -> listOf(error(IssueCode.INVALID_TYPE, "/submit", "must be an action object", SUBMIT_HINT))
    }
    return text(input, "id", "") + submit
}
