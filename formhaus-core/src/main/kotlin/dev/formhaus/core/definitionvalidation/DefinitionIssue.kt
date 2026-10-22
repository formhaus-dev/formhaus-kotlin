package dev.formhaus.core.definitionvalidation

@ConsistentCopyVisibility
public data class DefinitionIssue internal constructor(
    public val code: IssueCode,
    public val severity: Severity,
    public val path: String,
    public val message: String,
    public val hint: String?,
) {
    public enum class Severity { ERROR, WARNING }

    public val isError: Boolean get() = severity == Severity.ERROR
}

private fun issue(severity: DefinitionIssue.Severity, code: IssueCode, path: String, problem: String, hint: String?): DefinitionIssue {
    val sentence = hint?.let { if (it.endsWith("?")) it else "$it." }
    val message = "${path.ifEmpty { "/" }}: $problem." + (sentence?.let { " $it" } ?: "")
    return DefinitionIssue(code, severity, path, message, sentence)
}

internal fun warning(code: IssueCode, path: String, problem: String, hint: String? = null): DefinitionIssue =
    issue(DefinitionIssue.Severity.WARNING, code, path, problem, hint)
