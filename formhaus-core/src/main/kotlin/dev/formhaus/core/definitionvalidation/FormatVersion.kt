package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.regex.JsRegex

private val versionPattern = JsRegex.unchecked("^[1-9]\\d*(\\.\\d+)?$")

private fun majorVersion(version: String): Double = version.substringBefore('.').toDoubleOrNull() ?: 0.0

internal fun formatIssues(input: JsonObject): List<DefinitionIssue> {
    val version = input["formatVersion"] ?: return emptyList()
    val text = version.stringValue
    if (text == null || versionPattern.test(text) != true) {
        val hint = "Use \"${FormDefinition.SUPPORTED_FORMAT_VERSION}\""
        return listOf(error(IssueCode.INVALID_TYPE, "/formatVersion", "must be a version string", hint))
    }
    if (majorVersion(text) <= majorVersion(FormDefinition.SUPPORTED_FORMAT_VERSION)) return emptyList()
    return listOf(
        error(
            IssueCode.UNSUPPORTED_FORMAT_VERSION,
            "/formatVersion",
            "format $text is not supported",
            "Upgrade @formhaus/core",
        ),
    )
}
