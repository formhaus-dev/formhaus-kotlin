package dev.formhaus.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.form.LocalFormBehavior
import dev.formhaus.compose.model.FieldState
import dev.formhaus.compose.model.FormModel

private class FocusWait {
    var id: Int? = null
}

@Composable
public fun OnFormhausFocus(
    context: FieldContext,
    perform: () -> Unit,
) {
    OnFormhausFocus(context.state, context.model, context.isLocked, perform)
}

@Composable
internal fun OnFormhausFocus(
    state: FieldState,
    model: FormModel,
    disabled: Boolean,
    perform: () -> Unit,
) {
    val summary = LocalFormBehavior.current.errorSummary
    val action = rememberUpdatedState(perform)
    val wait = remember(state) { FocusWait() }
    LaunchedEffect(state, state.focusToken, disabled) {
        state.takeFocusRequest()?.let { wait.id = it }
        val id = wait.id ?: return@LaunchedEffect
        if (disabled) return@LaunchedEffect
        wait.id = null
        val request = model.focusRequest
        if (request == null || request.id != id || (request.summary && summary)) return@LaunchedEffect
        withFrameNanos {}
        action.value()
    }
}

@Composable
public fun Modifier.formhausFocus(
    context: FieldContext,
    requester: FocusRequester,
): Modifier {
    val state = context.state
    OnFormhausFocus(context) { requester.requestFocus() }
    DisposableEffect(state) { onDispose { state.noteFocus(false) } }
    return focusRequester(requester).onFocusChanged { state.noteFocus(it.hasFocus) }
}
