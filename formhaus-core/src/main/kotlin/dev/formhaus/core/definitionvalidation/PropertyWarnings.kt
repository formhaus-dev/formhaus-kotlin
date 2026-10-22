package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.isBlankText
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormSection
import dev.formhaus.core.regex.JsRegex
import dev.formhaus.core.validation.randomizedTypes

private val safeImage = JsRegex.unchecked("^(?i:https:|data:image\\/)")
private const val LABEL_HINT = "Add a label so screen readers can name the input"

private fun unknown(value: JsonObject, path: String, known: List<String>, hint: (String) -> String? = { null }): List<DefinitionIssue> {
    val absent = known.filter { it !in value }
    return value.keys.filter { it !in known }.map { key ->
        val fix = closest(key, absent)?.let { "Did you mean \"$it\"?" } ?: hint(key)
        warning(IssueCode.UNKNOWN_PROPERTY, "$path/${pointer(key)}", "unknown property \"$key\"", fix)
    }
}

private fun required(value: JsonObject, path: String, key: String, hint: String? = null): List<DefinitionIssue> =
    if (key in value) emptyList() else listOf(missing(path, key, hint, DefinitionIssue.Severity.WARNING))

private fun labeled(value: JsonObject, path: String, hint: String? = null): List<DefinitionIssue> =
    if ("label" in value) emptyList() else listOf(warning(IssueCode.MISSING_LABEL, "$path/label", "missing required property \"label\"", hint))

private fun action(value: JsonValue?, path: String, disables: Boolean = true): List<DefinitionIssue> {
    val json = value?.objectValue ?: return emptyList()
    val ignored = if (!disables && "disabled" in json) {
        listOf(warning(IssueCode.DISABLED_NOT_APPLICABLE, "$path/disabled", "the built-in renderers ignore \"disabled\" on this button", "Remove \"disabled\""))
    } else {
        emptyList()
    }
    return labeled(json, path) + unknown(json, path, Properties.action) + ignored
}

private fun misplaced(key: String): String? =
    if (key != "i18n" && key in Properties.validation) "Move \"$key\" into \"validation\": { \"$key\": ... }" else null

private fun optionWarnings(field: FormField, option: FieldOption, at: String): List<DefinitionIssue> {
    val issues = ArrayList(labeled(option.json, at) + unknown(option.json, at, Properties.option))
    val image = option.image
    if (image != null && safeImage.test(image) != true) {
        val problem = "the image of option \"${option.value.orEmpty()}\" in \"${field.key}\" is not an https: or data:image/ URL"
        issues.add(warning(IssueCode.UNSAFE_IMAGE_URL, "$at/image", problem, "Serve the image over https"))
    }
    if (option.pinned && !field.randomize) {
        issues.add(warning(IssueCode.RANDOMIZE_NOT_APPLICABLE, "$at/pinned", "\"${field.key}\" does not set \"randomize\"", "Remove \"pinned\""))
    }
    return issues
}

private fun inMatrix(entry: FieldEntry): Boolean = entry.parent?.json?.get("layout")?.stringValue == "matrix"

private fun randomizeWarnings(entry: FieldEntry): List<DefinitionIssue> {
    val field = entry.field
    if (!field.randomize || (field.type in randomizedTypes && !inMatrix(entry))) return emptyList()
    val problem = "\"randomize\" of \"${field.key}\" applies to radio, multiselect and select fields outside a matrix"
    return listOf(warning(IssueCode.RANDOMIZE_NOT_APPLICABLE, "${entry.path}/randomize", problem, "Remove \"randomize\""))
}

private fun fieldWarnings(entry: FieldEntry): List<DefinitionIssue> {
    val field = entry.field
    val path = entry.path
    val visible = field.type != "hidden"
    val issues = ArrayList(if (visible) labeled(field.json, path, LABEL_HINT) else emptyList())
    if (visible && field.label?.isBlankText == true) {
        issues.add(warning(IssueCode.MISSING_LABEL, "$path/label", "the label of \"${field.key}\" is empty", LABEL_HINT))
    }
    issues += unknown(field.json, path, if (field.isGroup) Properties.group else Properties.field, ::misplaced)
    for ((index, option) in field.options.orEmpty().withIndex()) issues += optionWarnings(field, option, "$path/options/$index")
    issues += randomizeWarnings(entry)
    field.validation?.let { issues += unknown(it.json, "$path/validation", Properties.validation) }
    if ("columns" in field.json && (!field.hasPictures || inMatrix(entry))) {
        val problem = "\"columns\" of \"${field.key}\" applies to radio and multiselect options with images outside a matrix"
        issues.add(warning(IssueCode.COLUMNS_NOT_APPLICABLE, "$path/columns", problem, "Remove \"columns\""))
    }
    return issues
}

internal fun propertyWarnings(context: CheckContext): List<DefinitionIssue> {
    val definition = context.definition
    val root = definition.json
    val issues = ArrayList(required(root, "", "title", "Add a title for host UI and editors"))
    val title = root["title"]
    if (title != null && title.stringValue == null) issues.add(warning(IssueCode.INVALID_TYPE, "/title", "\"title\" must be a string"))
    issues += unknown(root, "", Properties.form) + action(root["submit"], "/submit") + action(root["cancel"], "/cancel", disables = false)
    for ((index, step) in definition.steps.orEmpty().withIndex()) {
        val path = "/steps/$index"
        issues += required(step.json, path, "title") + unknown(step.json, path, Properties.step)
        for ((position, route) in step.routes.orEmpty().withIndex()) issues += unknown(route.json, "$path/routes/$position", Properties.route)
        for (name in listOf("next", "back", "skip")) issues += action(step.json[name], "$path/$name", disables = name == "next")
    }
    for (entry in context.nodes) {
        if (entry.node is FormSection) issues += required(entry.node.json, entry.path, "title") + unknown(entry.node.json, entry.path, Properties.section)
    }
    return issues + context.entries.flatMap(::fieldWarnings)
}
