package dev.formhaus.core.renderer

import dev.formhaus.core.json.ValueParts
import dev.formhaus.core.json.describe
import dev.formhaus.core.json.equalParts
import dev.formhaus.core.json.partsHash
import dev.formhaus.core.model.FormAction
import dev.formhaus.core.unicode.endsWithClosingPunctuation

public class ErrorEntry internal constructor(
    public val key: String,
    public val label: String,
    public val message: String,
) {
    public val id: String get() = key

    public val text: String
        get() {
            return label + (if (endsWithClosingPunctuation(label)) " " else ": ") + message
        }

    override fun equals(other: Any?): Boolean = equalParts(other, ErrorEntry::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("ErrorEntry")

    private fun parts(): ValueParts = listOf("key" to key, "label" to label, "message" to message)
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
