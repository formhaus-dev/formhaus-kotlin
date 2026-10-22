package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.json.ValueParts
import dev.formhaus.core.json.describe
import dev.formhaus.core.json.equalParts
import dev.formhaus.core.json.partsHash

public class DefinitionIssue internal constructor(
    public val code: IssueCode,
    public val severity: Severity,
    public val path: String,
    public val message: String,
    public val hint: String?,
) {
    public enum class Severity { ERROR, WARNING }

    public val isError: Boolean get() = severity == Severity.ERROR

    internal fun withSeverity(severity: Severity): DefinitionIssue = DefinitionIssue(code, severity, path, message, hint)

    override fun equals(other: Any?): Boolean = equalParts(other, DefinitionIssue::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("DefinitionIssue")

    private fun parts(): ValueParts = listOf("code" to code, "severity" to severity, "path" to path, "message" to message, "hint" to hint)
}

public class FormDefinitionError internal constructor(
    public val issues: List<DefinitionIssue>,
) : Exception("Invalid form definition:\n" + issues.joinToString("\n") { it.message })

private fun issue(
    severity: DefinitionIssue.Severity,
    code: IssueCode,
    path: String,
    problem: String,
    hint: String?,
): DefinitionIssue {
    val sentence = hint?.let { if (it.endsWith("?")) it else "$it." }
    val message = "${path.ifEmpty { "/" }}: $problem." + (sentence?.let { " $it" } ?: "")
    return DefinitionIssue(code, severity, path, message, sentence)
}

internal fun error(
    code: IssueCode,
    path: String,
    problem: String,
    hint: String? = null,
): DefinitionIssue = issue(DefinitionIssue.Severity.ERROR, code, path, problem, hint)

internal fun warning(
    code: IssueCode,
    path: String,
    problem: String,
    hint: String? = null,
): DefinitionIssue = issue(DefinitionIssue.Severity.WARNING, code, path, problem, hint)

internal fun pointer(key: String): String = key.replace("~", "~0").replace("/", "~1")
