package dev.formhaus.core.renderer

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.errorKeys
import dev.formhaus.core.engine.recalled
import dev.formhaus.core.engine.recalledTitle
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.ValueParts
import dev.formhaus.core.json.describe
import dev.formhaus.core.json.equalParts
import dev.formhaus.core.json.partsHash
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FormAction
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.unicode.endsWithClosingPunctuation

public class ErrorEntry internal constructor(
    public val key: String,
    public val id: String,
    public val label: String,
    public val message: String,
) {
    public val text: String
        get() {
            return label + (if (endsWithClosingPunctuation(label)) " " else ": ") + message
        }

    override fun equals(other: Any?): Boolean = equalParts(other, ErrorEntry::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("ErrorEntry")

    private fun parts(): ValueParts = listOf("key" to key, "id" to id, "label" to label, "message" to message)
}

public class ErrorGroup internal constructor(
    public val id: String,
    public val title: String,
    public val entries: List<ErrorEntry>,
) {
    override fun equals(other: Any?): Boolean = equalParts(other, ErrorGroup::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("ErrorGroup")

    private fun parts(): ValueParts = listOf("id" to id, "title" to title, "entries" to entries)
}

public class ErrorSummary internal constructor(
    public val groups: List<ErrorGroup>,
    public val topLevelErrors: List<String>,
    public val count: Int,
) {
    override fun equals(other: Any?): Boolean = equalParts(other, ErrorSummary::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("ErrorSummary")

    private fun parts(): ValueParts = listOf("groups" to groups, "topLevelErrors" to topLevelErrors, "count" to count)
}

public class ErrorSummaryOptions(
    public val idPrefix: String? = null,
    public val labels: LabelSource? = null,
) {
    override fun equals(other: Any?): Boolean = equalParts(other, ErrorSummaryOptions::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("ErrorSummaryOptions")

    private fun parts(): ValueParts = listOf("idPrefix" to idPrefix, "labels" to labels)
}

private class ErrorSection(
    val id: String,
    val title: String?,
    val fields: List<FormNode>,
)

private fun FormEngine.errorGroups(options: ErrorSummaryOptions): List<ErrorGroup> {
    val errors = core.errors
    if (errors.isEmpty()) return emptyList()
    val values = core.values.json
    val visibility = core.visibility
    val ids = DomIds(options.idPrefix, definition)
    val source = options.labels
    val sections =
        if (isMultiStep) {
            visibleSteps.map { ErrorSection(it.id, recalledTitle(it.json), it.fields) }
        } else {
            listOf(ErrorSection(definition.id, recalledTitle(definition.json), definition.fields.orEmpty()))
        }
    return sections.mapIndexedNotNull { index, section ->
        val entries =
            errorKeys(section.fields, errors, values).mapNotNull { key ->
                val item = visibility.activeItem(key, values)
                val raw = item?.field ?: visibility.activeField(key, values) ?: return@mapNotNull null
                val message = errors[key] ?: return@mapNotNull null
                val name = recalled(raw, if (item == null) null else key).label.orEmpty()
                val label = item?.let { "$name (${itemTitle(it.group, it.index, source)})" } ?: name
                val id = item?.let { ids.item(it.group.key, it.index, it.field.key) } ?: ids.field(key)
                ErrorEntry(key, id, label, message)
            }
        if (entries.isEmpty()) return@mapIndexedNotNull null
        val title =
            section.title?.takeIf { it.isNotEmpty() }
                ?: label("stepTitle", source, stringMapOf("index" to JsonValue.Number(index + 1)))
        ErrorGroup(section.id, title, entries)
    }
}

public fun FormEngine.errorSummary(options: ErrorSummaryOptions = ErrorSummaryOptions()): ErrorSummary {
    val groups = errorGroups(options)
    return ErrorSummary(groups, topLevelErrors, groups.sumOf { it.entries.size })
}

public class FormActionsState internal constructor(
    public val submitAction: FormAction,
    public val nextAction: FormAction?,
    public val backAction: FormAction?,
    public val skipAction: FormAction?,
    public val cancelAction: FormAction?,
    public val isFirstStep: Boolean,
    public val isLastStep: Boolean,
    public val isMultiStep: Boolean,
    public val primaryLabel: String,
    public val primaryDisabled: Boolean,
    public val showPrimary: Boolean,
    public val showBack: Boolean,
    public val backLabel: String,
    public val showSkip: Boolean,
    public val skipLabel: String,
) {
    override fun equals(other: Any?): Boolean = equalParts(other, FormActionsState::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("FormActionsState")

    private fun parts(): ValueParts =
        listOf(
            "submitAction" to submitAction,
            "nextAction" to nextAction,
            "backAction" to backAction,
            "skipAction" to skipAction,
            "cancelAction" to cancelAction,
            "isFirstStep" to isFirstStep,
            "isLastStep" to isLastStep,
            "isMultiStep" to isMultiStep,
            "primaryLabel" to primaryLabel,
            "primaryDisabled" to primaryDisabled,
            "showPrimary" to showPrimary,
            "showBack" to showBack,
            "backLabel" to backLabel,
            "showSkip" to showSkip,
            "skipLabel" to skipLabel,
        )
}
