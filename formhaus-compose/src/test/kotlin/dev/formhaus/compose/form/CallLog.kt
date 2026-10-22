package dev.formhaus.compose.form

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import dev.formhaus.compose.components.OnFormhausFocus
import dev.formhaus.compose.model.FormModel
import kotlinx.coroutines.launch

class CallLog {
    val calls = mutableListOf<String>()
    val names = mutableSetOf<String>()
}

fun ComposeContentTestRule.act(
    form: FormModel,
    action: suspend FormModel.() -> Unit,
) {
    runOnIdle { form.scope.launch { form.action() } }
    waitForIdle()
}

@Composable
fun Provided(
    components: FormComponents = FormComponents.standard,
    behavior: FormBehavior = FormBehavior(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFormComponents provides components, LocalFormBehavior provides behavior, content = content)
}

fun probes(
    log: CallLog,
    base: FormComponents = FormComponents.standard,
): FormComponents =
    base.field("probe") { context ->
        log.calls += "body:${context.key}"
        OnFormhausFocus(context) { log.calls += "focus:${context.key}" }
        Text(context.text)
    }

fun twoSteps(required: Boolean): String =
    """
    {"id": "p", "title": "P", "submit": {"label": "Send"},
     "steps": [
       {"id": "one", "title": "One", "fields": [{"key": "a", "type": "probe", "label": "A", "validation": {"required": $required}}]},
       {"id": "two", "title": "Two", "fields": [{"key": "b", "type": "probe", "label": "B"}]}
     ]}
    """.trimIndent()

val textSteps =
    """
    {"id": "s", "title": "S", "submit": {"label": "Send"},
     "steps": [
       {"id": "one", "title": "One", "fields": [{"key": "a", "type": "text", "label": "A", "validation": {"required": true}}]},
       {"id": "two", "title": "Two", "fields": [{"key": "b", "type": "text", "label": "B"}]}
     ]}
    """.trimIndent()
