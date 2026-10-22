package dev.formhaus.compose.model

import dev.formhaus.core.renderer.firstErrorKey

internal class FocusRequests {
    var counter = 0
    var pendingKey: String? = null
}

internal fun FormModel.errorText(key: String): String? =
    errorGroups.firstNotNullOfOrNull { group -> group.entries.firstOrNull { it.key == key }?.text }

internal fun FormModel.requestFocus(
    key: String?,
    afterErrors: Boolean = false,
) {
    val requests = focusRequests
    requests.counter += 1
    val summary = afterErrors && key != null && errorGroups.sumOf { it.entries.size } > 1
    focusRequest = FocusRequest(key, requests.counter, summary)
    requests.pendingKey?.takeIf { it != key }?.let { fieldStates[it]?.dropFocusRequest() }
    requests.pendingKey = key
    if (key != null) field(key).requestFocus(requests.counter)
}

internal fun FormModel.dropFocusRequest() {
    if (focusRequest == null) return
    focusRequest = null
    focusRequests.pendingKey?.let { fieldStates[it]?.dropFocusRequest() }
    focusRequests.pendingKey = null
}

internal fun FormModel.noteInvalidAttempt() {
    invalidAttempts += 1
    requestFocus(engine.firstErrorKey(), afterErrors = true)
}
