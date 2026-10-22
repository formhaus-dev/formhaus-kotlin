package dev.formhaus.core.i18n

internal val defaultLabels: Map<String, String> =
    linkedMapOf(
        "continue" to "Continue",
        "back" to "Back",
        "stepOf" to "Step {current} of {total}",
        "errorSummary" to "Check these answers",
        "addItem" to "Add",
        "removeItem" to "Remove",
        "itemLabel" to "{label} {index}",
        "itemAdded" to "{item} added",
        "itemRemoved" to "{item} removed",
        "removeItemNamed" to "{label} {item}",
        "removeItemPrompt" to "Remove {item}?",
        "removeItemBody" to "This removes {item} and its answers. This cannot be undone.",
        "cancel" to "Cancel",
        "enlargeImage" to "Enlarge image: {label}",
        "close" to "Close",
        "clear" to "Clear",
        "done" to "Done",
        "chooseFile" to "Choose file",
        "required" to "required",
        "skip" to "Skip",
        "submit" to "Submit",
        "stepTitle" to "Step {index}",
        "stepErrors" to "Errors: {count}",
        "or" to "or",
        "select" to "Select",
    )

internal val defaultLabelKeys: List<String> = defaultLabels.keys.toList()
