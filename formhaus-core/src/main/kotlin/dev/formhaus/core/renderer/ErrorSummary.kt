package dev.formhaus.core.renderer

import dev.formhaus.core.model.FormAction
import dev.formhaus.core.unicode.endsWithClosingPunctuation

@ConsistentCopyVisibility
public data class ErrorEntry internal constructor(
    public val key: String,
    public val label: String,
    public val message: String,
) {
    public val id: String get() = key

    public val text: String
        get() {
            return label + (if (endsWithClosingPunctuation(label)) " " else ": ") + message
        }
}

@ConsistentCopyVisibility
public data class ErrorGroup internal constructor(
    public val id: String,
    public val title: String,
    public val entries: List<ErrorEntry>,
)

@ConsistentCopyVisibility
public data class FormActionsState internal constructor(
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
)
