package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.engine.recallKeys
import dev.formhaus.core.engine.recallSlots
import dev.formhaus.core.engine.reservedTokens
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.model.FormField

internal class RecallText(
    val path: String,
    val keys: List<String>,
    val reserved: List<String>,
    val step: Int,
    val owner: Int,
    val group: String?,
)

private class RecallOwner(
    val json: JsonObject,
    val ordinal: Int,
    val key: String?,
    val path: String,
    val step: Int,
    val group: String?,
)

internal fun recallTexts(context: CheckContext): List<RecallText> {
    val steps =
        context.definition.steps.orEmpty().mapIndexed {
            index,
            step,
            ->
            RecallOwner(step.json, step.ordinal, null, "/steps/$index", index, null)
        }
    val nodes =
        context.nodes.map {
            RecallOwner(it.node.json, it.node.ordinal, it.node.key, it.path, it.step, it.group?.key)
        }
    return (steps + nodes).flatMap { owner ->
        recallSlots.mapNotNull { slot ->
            val text = owner.json[slot]?.stringValue ?: return@mapNotNull null
            val keys = recallKeys(text)
            val reserved = reservedTokens(text)
            if (keys.isEmpty() && reserved.isEmpty()) return@mapNotNull null
            RecallText(
                "${owner.path}/$slot",
                keys,
                reserved,
                owner.step,
                owner.ordinal,
                if (slot ==
                    "itemLabel"
                ) {
                    owner.key
                } else {
                    owner.group
                },
            )
        }
    }
}

private class SharedFields(
    context: CheckContext,
) {
    private val fields = HashMap<Pair<String?, String>, MutableList<FormField>>()

    init {
        for (entry in context.entries) {
            fields
                .getOrPut(
                    entry.group?.key to entry.field.key,
                ) { ArrayList() }
                .add(entry.field)
        }
    }

    operator fun get(
        group: String?,
        key: String,
    ): List<FormField> = fields[group to key].orEmpty()
}

private val untrustedSources = listOf("referrer", "pageTitle")

private fun keyWarnings(
    context: CheckContext,
    shared: SharedFields,
    text: RecallText,
): List<DefinitionIssue> =
    text.keys.distinct().flatMap { key ->
        val token = "\"{{$key}}\""
        val target =
            context.target(key, text.group)
                ?: return@flatMap listOf(
                    warning(
                        IssueCode.UNKNOWN_RECALL_FIELD,
                        text.path,
                        "$token names no field",
                        "Use the key of an earlier field",
                    ),
                )
        val fields = shared[target.group?.key, key]
        val issues = ArrayList<DefinitionIssue>()
        if (target.field.ordinal == text.owner || target.step > text.step) {
            val problem = "$token recalls a field that is not answered yet"
            issues.add(
                warning(IssueCode.RECALL_FORWARD_REFERENCE, text.path, problem, "Recall a field from an earlier step"),
            )
        }
        if (fields.any { it.type == "password" }) {
            issues.add(
                warning(
                    IssueCode.RECALL_SENSITIVE_FIELD,
                    text.path,
                    "$token recalls a password, which shows nothing",
                    "Remove the token",
                ),
            )
        }
        if (fields.any { it.type == "hidden" && it.source?.objectValue != null }) {
            val problem = "$token shows text from the page URL, which anyone can write in a link"
            issues.add(
                warning(
                    IssueCode.RECALL_QUERY_SOURCE,
                    text.path,
                    problem,
                    "Recall it only where any text is safe to show",
                ),
            )
        }
        if (fields.any { it.type == "hidden" && it.source?.stringValue in untrustedSources }) {
            val problem = "$token shows the referring page or the page title, which another site can set"
            issues.add(
                warning(
                    IssueCode.RECALL_UNTRUSTED_SOURCE,
                    text.path,
                    problem,
                    "Recall it only where any text is safe to show",
                ),
            )
        }
        issues
    }

internal fun recallWarnings(context: CheckContext): List<DefinitionIssue> {
    if (!context.definition.recallsAnswers) return emptyList()
    val shared = SharedFields(context)
    return recallTexts(context).flatMap { text ->
        val syntax =
            text.reserved.take(1).map { token ->
                val hint = "Put only a field key inside {{ }}, or write \\{{ for literal braces"
                warning(
                    IssueCode.RECALL_SYNTAX,
                    text.path,
                    "\"$token\" holds more than a field key and shows as written",
                    hint,
                )
            }
        (syntax + keyWarnings(context, shared, text)).distinctBy { it.code }
    }
}
