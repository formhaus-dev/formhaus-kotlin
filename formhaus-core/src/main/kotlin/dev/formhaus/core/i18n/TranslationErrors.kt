package dev.formhaus.core.i18n

import dev.formhaus.core.definitionvalidation.DefinitionIssue
import dev.formhaus.core.definitionvalidation.IssueCode
import dev.formhaus.core.definitionvalidation.error
import dev.formhaus.core.definitionvalidation.pointer
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.isBlankText
import dev.formhaus.core.model.FormDefinition

private fun strings(
    value: JsonValue?,
    path: String,
    what: String,
    nested: List<String> = emptyList(),
): List<DefinitionIssue> {
    value ?: return emptyList()
    val json = value.objectValue ?: return listOf(error(IssueCode.INVALID_TYPE, path, "must be an object of $what"))
    return json.flatMap { (key, item) ->
        when {
            key in nested -> strings(item, "$path/$key", what)
            item.stringValue?.isBlankText == false -> emptyList()
            else -> listOf(error(IssueCode.INVALID_TYPE, "$path/${pointer(key)}", "must be a non-empty string"))
        }
    }
}

internal fun translationErrors(definition: FormDefinition): List<DefinitionIssue> {
    val root = definition.json
    val issues = ArrayList<DefinitionIssue>()
    val lang = root["lang"]
    if (lang != null &&
        lang.stringValue == null
    ) {
        issues.add(error(IssueCode.INVALID_TYPE, "/lang", "\"lang\" must be a string"))
    }
    issues +=
        strings(root["messages"], "/messages", "message templates") +
        strings(root["labels"], "/labels", "label templates")
    for (text in textNodes(definition)) {
        val block = text.node["i18n"]
        val texts = if (text.kind == NodeKind.VALIDATION) emptyList() else text.kind.slots
        val unbound =
            block
                ?.objectValue
                ?.let { json ->
                    texts.filter { it in json && text.node[it]?.stringValue == null }
                }.orEmpty()
        val nested = if (text.kind == NodeKind.FORM) listOf("messages", "labels") else emptyList()
        issues += strings(block, "${text.path}/i18n", "translation keys", nested)
        for (slot in unbound) {
            val problem = "\"$slot\" has a translation key but no source text"
            issues.add(
                error(
                    IssueCode.I18N_KEY_WITHOUT_TEXT,
                    "${text.path}/i18n/$slot",
                    problem,
                    "Add \"$slot\" as the fallback text",
                ),
            )
        }
    }
    return issues
}
