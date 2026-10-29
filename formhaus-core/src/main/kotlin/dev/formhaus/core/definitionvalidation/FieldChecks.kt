package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.isBlankText
import dev.formhaus.core.json.isInteger
import dev.formhaus.core.json.jsNumberString
import dev.formhaus.core.json.quoteJson
import dev.formhaus.core.model.FieldValidation
import dev.formhaus.core.model.ScaleRange
import dev.formhaus.core.regex.JsRegex
import dev.formhaus.core.validation.MAX_GROUP_ITEMS
import dev.formhaus.core.validation.maxItems

internal val sourceNames = listOf("referrer", "page", "pageTitle", "language", "timezone", "device", "country")

private const val MAX_SCALE_SPAN = 11

private val fileListRules = listOf("multiple" to ShapeKind.BOOLEAN)

private fun ScaleRange.isValid(): Boolean = min.isInteger && max.isInteger && max > min && max - min < MAX_SCALE_SPAN

private fun rangeIssue(
    rules: FieldValidation,
    path: String,
    min: String,
    max: String,
): List<DefinitionIssue> {
    val low = rules.json[min]?.numberValue ?: return emptyList()
    val high = rules.json[max]?.numberValue ?: return emptyList()
    if (low <= high) return emptyList()
    val problem = "$min (${jsNumberString(low)}) is greater than $max (${jsNumberString(high)})"
    return listOf(error(IssueCode.MIN_GREATER_THAN_MAX, "$path/validation/$min", problem, "Lower $min or raise $max"))
}

private fun patternIssue(
    pattern: String?,
    path: String,
): List<DefinitionIssue> {
    if (pattern == null || JsRegex.compile(pattern) != null) return emptyList()
    val problem = "${quoteJson(pattern)} is not a valid regular expression"
    return listOf(
        error(
            IssueCode.INVALID_PATTERN,
            "$path/validation/pattern",
            problem,
            "Fix the pattern or escape special characters with a backslash",
        ),
    )
}

private fun ruleIssues(
    context: CheckContext,
    entry: FieldEntry,
): List<DefinitionIssue> {
    val field = entry.field
    val path = entry.path
    val rules = field.validation ?: FieldValidation(MutableJsonObject())
    val issues =
        ArrayList(
            rangeIssue(rules, path, "minLength", "maxLength") + rangeIssue(rules, path, "min", "max") +
                patternIssue(rules.pattern, path),
        )
    if (context.definition.listsFiles) issues += typedProperties(field.json, fileListRules, path)
    val range = field.scaleRange
    if (range != null && !range.isValid()) {
        issues.add(error(IssueCode.INVALID_SCALE, "$path/validation", "a scale needs 2 to 11 whole-number points"))
    }
    rules.matchField?.let {
        issues +=
            missingTarget(context, it, "$path/validation/matchField", group = entry.group?.key)
    }
    val items = if (field.isGroup) field.defaultItems else null
    val max = rules.maxLength
    if (items != null && max != null && items > max) {
        val problem = "defaultItems (${jsNumberString(items)}) is greater than maxLength (${jsNumberString(max)})"
        issues.add(
            error(
                IssueCode.DEFAULT_ITEMS_GREATER_THAN_MAX,
                "$path/defaultItems",
                problem,
                "Lower defaultItems or raise maxLength",
            ),
        )
    }
    return issues
}

internal fun itemLimitWarnings(entry: FieldEntry): List<DefinitionIssue> {
    val field = entry.field
    if (!field.isGroup) return emptyList()
    val max = field.validation?.maxLength
    val limit = "the ${jsNumberString(field.maxItems)}-item limit"
    val issues = ArrayList<DefinitionIssue>()
    if (max != null && max > MAX_GROUP_ITEMS) {
        val problem = "maxLength (${jsNumberString(max)}) is greater than $limit"
        issues.add(
            warning(
                IssueCode.GROUP_ITEM_LIMIT,
                "${entry.path}/validation/maxLength",
                problem,
                "Lower maxLength to $MAX_GROUP_ITEMS",
            ),
        )
    }
    val items = field.defaultItems
    if (items != null && items > field.maxItems && (max == null || items <= max)) {
        val hint =
            if (max == null) {
                "Lower defaultItems or set validation.maxLength"
            } else {
                "Lower defaultItems to $MAX_GROUP_ITEMS"
            }
        issues.add(
            warning(
                IssueCode.GROUP_ITEM_LIMIT,
                "${entry.path}/defaultItems",
                "defaultItems (${jsNumberString(items)}) is greater than $limit",
                hint,
            ),
        )
    }
    return issues
}

private fun optionIssues(entry: FieldEntry): List<DefinitionIssue> {
    val seen = HashSet<String>()
    var seenMissing = false
    return entry.field.options.orEmpty().map { it.value }.withIndex().mapNotNull { (index, value) ->
        val repeated = if (value != null) !seen.add(value) else seenMissing.also { seenMissing = true }
        if (!repeated) return@mapNotNull null
        error(
            IssueCode.DUPLICATE_OPTION_VALUE,
            "${entry.path}/options/$index/value",
            "\"${value.orEmpty()}\" is already an option value",
        )
    }
}

internal fun fieldIssues(context: CheckContext): List<DefinitionIssue> =
    context.entries.flatMap {
        ruleIssues(context, it) +
            optionIssues(it)
    }

private const val QUERY_HINT = "Use { \"query\": \"<parameter name>\" } for a URL parameter"

internal fun sourceIssues(context: CheckContext): List<DefinitionIssue> =
    context.entries.flatMap { entry ->
        val source = entry.field.source ?: return@flatMap emptyList()
        val at = "${entry.path}/source"
        val name = source.stringValue
        if (name != null) {
            if (name in sourceNames) return@flatMap emptyList()
            val hint = if (name == "query") QUERY_HINT else suggest(name, sourceNames) ?: oneOf(sourceNames)
            return@flatMap listOf(error(IssueCode.UNKNOWN_SOURCE, at, "\"$name\" is not a source", hint))
        }
        val json = source.objectValue
        val query = json?.get("query")?.stringValue
        if (json != null && json.size == 1 && query != null && !query.isBlankText) return@flatMap emptyList()
        listOf(error(IssueCode.INVALID_TYPE, at, "must be a source name or a query object", QUERY_HINT))
    }

internal fun sourceWarnings(entry: FieldEntry): List<DefinitionIssue> {
    if (entry.field.source == null || entry.field.type == "hidden") return emptyList()
    val hint = "Set \"type\" to \"hidden\" or remove \"source\""
    return listOf(
        warning(IssueCode.SOURCE_NOT_APPLICABLE, "${entry.path}/source", "only hidden fields read \"source\"", hint),
    )
}
