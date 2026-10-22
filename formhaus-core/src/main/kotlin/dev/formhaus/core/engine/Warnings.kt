package dev.formhaus.core.engine

import dev.formhaus.core.definitionvalidation.CheckDefinitionOptions
import dev.formhaus.core.definitionvalidation.DefinitionIssue
import dev.formhaus.core.definitionvalidation.IssueCode
import dev.formhaus.core.definitionvalidation.checkDefinition
import dev.formhaus.core.definitionvalidation.warning
import dev.formhaus.core.i18n.hasText
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.renderer.LabelSource
import dev.formhaus.core.validation.MessageKey
import dev.formhaus.core.validation.MessageSource
import dev.formhaus.core.validation.ResolveMessage
import dev.formhaus.core.validation.messageResolver
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

internal typealias MissingMessageFn = (MessageKey) -> Unit

private val warningLog = Logger.getLogger("dev.formhaus.definition")
private const val REPORTED_TEXT_LIMIT = 1024
private val reportedTexts = ConcurrentHashMap.newKeySet<String>()

private fun reportsFirst(text: String): Boolean {
    if (reportedTexts.size >= REPORTED_TEXT_LIMIT) reportedTexts.clear()
    return reportedTexts.add(text)
}

internal fun deliver(
    issue: DefinitionIssue,
    options: FormEngineOptions,
) {
    val onWarning = options.onWarning
    if (onWarning != null) onWarning(issue) else warningLog.warning("[formhaus] ${issue.message}")
}

internal fun reportWarnings(
    definition: FormDefinition,
    options: FormEngineOptions,
) {
    val issues = checkDefinition(JsonValue.Object(definition.json), CheckDefinitionOptions(options.validators.keys))
    val debugIssues = if (options.debugChecks) unorderedWarning(definition, options) else emptyList()
    for (issue in issues + debugIssues) deliver(issue, options)
}

private fun reportMissing(
    code: IssueCode,
    key: String,
    catalog: String,
    options: FormEngineOptions,
) {
    if (!reportsFirst("${code.rawValue}:$key")) return
    deliver(
        warning(code, "", "The $catalog catalog has no \"$key\" text", "Add \"$key\" to the $catalog catalog"),
        options,
    )
}

private val LabelSource?.catalog: JsonValue? get() = (this as? LabelSource.Catalog)?.catalog

private val MessageSource?.catalog: JsonValue? get() = (this as? MessageSource.Catalog)?.catalog

internal fun reportMissingLabel(
    key: String,
    source: LabelSource?,
    definition: FormDefinition,
    options: FormEngineOptions,
) {
    val catalog = source.catalog ?: return
    if (hasText(catalog, key) || definition.labels?.let { hasText(it, key) } == true) return
    reportMissing(IssueCode.MISSING_LABEL_KEY, key, "labels", options)
}

internal fun missingMessageReporter(options: FormEngineOptions): MissingMessageFn? {
    if (!options.debugChecks || options.messages.catalog == null) return null
    return { key -> reportMissing(IssueCode.MISSING_MESSAGE_KEY, key.rawValue, "messages", options) }
}

internal fun watchedResolver(
    source: MessageSource?,
    definition: FormDefinition,
    missing: MissingMessageFn?,
): ResolveMessage {
    val resolve = messageResolver(source, MessageSource.Catalog(definition.messages ?: JsonValue.Null))
    val catalog = source.catalog
    if (missing == null || catalog == null) return resolve
    val own = definition.messages ?: JsonValue.Null
    return { key, params, field ->
        if (!hasText(catalog, key.rawValue) && !hasText(own, key.rawValue)) missing(key)
        resolve(key, params, field)
    }
}
