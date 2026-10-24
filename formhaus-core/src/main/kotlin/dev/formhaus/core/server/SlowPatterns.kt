package dev.formhaus.core.server

import dev.formhaus.core.definitionvalidation.CheckContext
import dev.formhaus.core.definitionvalidation.DefinitionIssue
import dev.formhaus.core.definitionvalidation.FormDefinitionError
import dev.formhaus.core.definitionvalidation.IssueCode
import dev.formhaus.core.definitionvalidation.SLOW_PATTERN_LENGTH_LIMIT
import dev.formhaus.core.definitionvalidation.error
import dev.formhaus.core.definitionvalidation.slowPattern
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.regex.ScopedLocal
import dev.formhaus.core.validation.MAX_STRING_LENGTH

internal enum class PatternVerdict { REFUSED, QUADRATIC, SAFE }

internal const val QUADRATIC_VALUE_LENGTH = 1000

private const val VERDICT_CACHE_SIZE = 256

private val verdicts = HashMap<String, PatternVerdict>()

internal fun patternVerdict(pattern: String): PatternVerdict {
    if (pattern.length > SLOW_PATTERN_LENGTH_LIMIT) return PatternVerdict.REFUSED
    synchronized(verdicts) { verdicts[pattern] }?.let { return it }
    val result =
        when {
            slowPattern(pattern) == null -> PatternVerdict.SAFE
            isQuadratic(pattern) -> PatternVerdict.QUADRATIC
            else -> PatternVerdict.REFUSED
        }
    synchronized(verdicts) {
        if (verdicts.size >= VERDICT_CACHE_SIZE) verdicts.clear()
        verdicts[pattern] = result
    }
    return result
}

internal object QuadraticPatterns {
    val current: ScopedLocal<Set<String>> = ScopedLocal(emptySet())

    fun valueLimit(pattern: String): Int {
        val quadratic = current.value
        return if (quadratic.isNotEmpty() && pattern in quadratic) QUADRATIC_VALUE_LENGTH else MAX_STRING_LENGTH
    }
}

internal fun refuseSlowPatterns(definition: FormDefinition): Set<String> {
    val quadratic = HashSet<String>()
    val issues = ArrayList<DefinitionIssue>()
    for (entry in CheckContext(definition).entries) {
        val pattern = entry.field.validation?.pattern ?: continue
        when (patternVerdict(pattern)) {
            PatternVerdict.REFUSED -> {
                issues +=
                    error(
                        IssueCode.SLOW_PATTERN,
                        "${entry.path}/validation/pattern",
                        "the pattern of \"${entry.field.key}\" can take very long on some values, so the server does not run it",
                        "Rewrite it without nested or overlapping repeats, in 1000 characters or less",
                    )
            }

            PatternVerdict.QUADRATIC -> {
                quadratic += pattern
            }

            PatternVerdict.SAFE -> {}
        }
    }
    if (issues.isNotEmpty()) throw FormDefinitionError(issues)
    return quadratic
}
