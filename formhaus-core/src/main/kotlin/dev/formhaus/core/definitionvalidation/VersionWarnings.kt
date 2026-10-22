package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.formatParts
import dev.formhaus.core.model.scaleTypes

internal val containsOperators = listOf("contains", "notContains")

private const val UPGRADE = "Upgrade the server that runs validateSubmission() first"

private class Outdated(
    val problem: String,
    val needed: Int,
    val hint: String,
)

private fun latestOutdated(context: CheckContext): Outdated? {
    val recall = recallTexts(context).firstOrNull { it.keys.isNotEmpty() }
    if (recall != null) {
        return Outdated(
            "${recall.path} recalls \"{{${recall.keys.first()}}}\"",
            5,
            "Below 1.5 the text shows the braces as written",
        )
    }
    context.entries
        .firstOrNull { entry ->
            entry.field.options
                .orEmpty()
                .any { it.exclusive }
        }?.let {
            return Outdated(
                "${it.path} has an exclusive option",
                5,
                "Older versions of @formhaus/core ignore \"exclusive\". $UPGRADE",
            )
        }
    return context.entries.firstOrNull { it.field.autocomplete != null }?.let {
        Outdated(
            "${it.path}/autocomplete is set",
            5,
            "Older versions of @formhaus/core report \"autocomplete\" as an unknown property",
        )
    }
}

private fun setsOrder(field: FormField): Boolean = "randomize" in field.json || field.options.orEmpty().any { "pinned" in it.json }

private fun outdated(
    context: CheckContext,
    minor: Double,
): Outdated? {
    if (minor < 6) {
        context.entries.firstOrNull { setsOrder(it.field) }?.let {
            return Outdated(
                "${it.path} sets \"randomize\" or \"pinned\"",
                6,
                "Older versions of @formhaus/core show the written order",
            )
        }
    }
    if (minor < 5) latestOutdated(context)?.let { return it }
    if (minor < 4) {
        for (entry in context.conditions) {
            val name = containsOperators.firstOrNull { it in entry.condition.json } ?: continue
            return Outdated(
                "${entry.path} uses \"$name\"",
                4,
                "Older versions of @formhaus/core reject this operator. $UPGRADE",
            )
        }
    }
    if (minor < 3) {
        context.nodes.firstOrNull { it.node.type in scaleTypes }?.let {
            val hint = "Older versions of @formhaus/core treat this type as a custom type and do not validate it. $UPGRADE"
            return Outdated("${it.path} is a ${it.node.type}", 3, hint)
        }
    }
    return context.nodes.firstOrNull { it.node.type == "section" || it.node.type == "group" }?.let {
        Outdated(
            "${it.path} is a ${it.node.type}",
            1,
            "so that older versions of @formhaus/core reject the definition instead of skipping the fields inside",
        )
    }
}

internal fun versionWarnings(context: CheckContext): List<DefinitionIssue> {
    val (major, minor) = formatParts(context.definition.json["formatVersion"])
    val outdated = outdated(context, minor) ?: return emptyList()
    if (major != 1.0 || minor >= outdated.needed) return emptyList()
    val text = "Set \"formatVersion\": \"1.${outdated.needed}\"${if (outdated.needed > 1) "." else ","} ${outdated.hint}"
    return listOf(
        warning(
            IssueCode.OUTDATED_FORMAT_VERSION,
            "/formatVersion",
            "${outdated.problem}, which needs format 1.${outdated.needed}",
            text,
        ),
    )
}
