package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue

internal enum class ShapeKind(
    val label: String,
    val accepts: (JsonValue) -> Boolean,
) {
    STRING("a string", { it.stringValue != null }),
    NUMBER("a number", { it.isFiniteNumber }),
    BOOLEAN("true or false", { it.boolValue != null }),
    REQUIREMENT("true, false or a message string", { it.boolValue != null || it.stringValue != null }),
    SCALAR("a string, number or boolean", { it.typeName in listOf("string", "number", "boolean") }),
    LIST("an array of strings or numbers", { value ->
        value.arrayValue?.all {
            it.stringValue != null ||
                it.numberValue != null
        } ==
            true
    }),
}

internal typealias ShapeCheck = (JsonObject, String) -> List<DefinitionIssue>

internal fun typedProperties(
    value: JsonObject,
    rules: List<Pair<String, ShapeKind>>,
    path: String,
): List<DefinitionIssue> =
    rules.mapNotNull { (key, kind) ->
        val item = value[key]
        if (item == null ||
            kind.accepts(item)
        ) {
            null
        } else {
            error(IssueCode.INVALID_TYPE, "$path/${pointer(key)}", "\"$key\" must be ${kind.label}")
        }
    }

internal fun missing(
    path: String,
    key: String,
    hint: String? = null,
    severity: DefinitionIssue.Severity = DefinitionIssue.Severity.ERROR,
) = error(IssueCode.MISSING_PROPERTY, path, "missing required property \"$key\"", hint).withSeverity(severity)

internal fun objectList(
    value: JsonValue?,
    path: String,
    name: String,
    check: ShapeCheck,
    required: Boolean = false,
): List<DefinitionIssue> {
    if (!required && (value == null || value.isNull)) return emptyList()
    if (value == null) {
        val key = path.substringAfterLast('/')
        return listOf(missing(path.dropLast(key.length + 1), key))
    }
    val items =
        value.arrayValue
            ?: return listOf(
                error(
                    IssueCode.INVALID_TYPE,
                    path,
                    "must be an array",
                    if (required) "Use [] when there is nothing to list" else null,
                ),
            )
    return items.withIndex().flatMap { (index, item) ->
        val json =
            item.objectValue ?: return@flatMap listOf(error(IssueCode.INVALID_TYPE, "$path/$index", "must be $name"))
        check(json, "$path/$index")
    }
}

internal fun requiredString(
    value: JsonObject,
    key: String,
    path: String,
): List<DefinitionIssue> {
    val item = value[key] ?: return listOf(missing(path, key))
    return if (item.stringValue != null) {
        emptyList()
    } else {
        listOf(error(IssueCode.INVALID_TYPE, "$path/${pointer(key)}", "\"$key\" must be a string"))
    }
}
