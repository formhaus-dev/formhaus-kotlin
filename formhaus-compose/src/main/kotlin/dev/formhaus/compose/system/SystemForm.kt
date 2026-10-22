package dev.formhaus.compose.system

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.containers.FormProgress
import dev.formhaus.compose.containers.StepBackHandler
import dev.formhaus.compose.form.ActionsContext
import dev.formhaus.compose.form.BackPlacement
import dev.formhaus.compose.form.FORM_TOP_ANCHOR
import dev.formhaus.compose.form.LocalFormBehavior
import dev.formhaus.compose.form.LocalFormComponents
import dev.formhaus.compose.form.TitleDisplayMode
import dev.formhaus.compose.form.formhausScrollAnchor
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.theme.LocalFormhausTokens
import kotlin.math.abs

private const val SLIDE = 0.3f
private const val SLIDE_MILLIS = 350

@Composable
internal fun SystemForm(
    model: FormModel,
    modifier: Modifier,
) {
    val behavior = LocalFormBehavior.current
    val slots = LocalFormComponents.current.slots
    val structure = model.structure
    val actions = ActionsContext(model)
    val shift = rememberStepShift(structure.stepIndex)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        SystemPage(model, modifier, shift)
    }
    StepBackHandler(actions, behavior.backPlacement != BackPlacement.BOTTOM && slots.actions == null)
    SystemErrorAlert(model)
}

@Composable
private fun SystemPage(
    model: FormModel,
    modifier: Modifier,
    shift: () -> Float,
) {
    val behavior = LocalFormBehavior.current
    val slots = LocalFormComponents.current.slots
    val structure = model.structure
    Column(
        modifier
            .fillMaxWidth()
            .formhausScrollAnchor(FORM_TOP_ANCHOR)
            .graphicsLayer {
                translationX = shift() * size.width * SLIDE
                alpha = 1 - abs(shift())
            },
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SystemTitle(model.definition.title, behavior.titleDisplayMode)
        if (slots.progress != null) FormProgress(model)
        SystemStepHeader(model)
        if (behavior.errorSummary) SystemErrorSummary(model)
        key(structure.revision) { SystemNodes(structure.nodes.filter { it.type != "hidden" }, model) }
        val actions = ActionsContext(model)
        slots.actions?.invoke(actions) ?: SystemActions(actions)
    }
}

@Composable
private fun rememberStepShift(index: Int): () -> Float {
    val reduceMotion = LocalFormhausTokens.current.reduceMotion
    val shift = remember { Animatable(0f) }
    var last by remember { mutableIntStateOf(index) }
    LaunchedEffect(index) {
        if (index == last) return@LaunchedEffect
        val forward = index > last
        last = index
        if (reduceMotion) return@LaunchedEffect
        shift.snapTo(if (forward) 1f else -1f)
        shift.animateTo(0f, tween(SLIDE_MILLIS))
    }
    return { shift.value }
}

@Composable
private fun SystemTitle(
    title: String?,
    mode: TitleDisplayMode,
) {
    if (title.isNullOrEmpty() || mode == TitleDisplayMode.OFF) return
    val typography = MaterialTheme.typography
    Text(
        title,
        Modifier.semantics { heading() },
        color = MaterialTheme.colorScheme.onSurface,
        style = if (mode == TitleDisplayMode.INLINE) typography.titleLarge else typography.headlineSmall,
    )
}

@Composable
private fun SystemErrorAlert(model: FormModel) {
    var dismissed by remember { mutableIntStateOf(-1) }
    val errors = model.topLevelErrors + listOfNotNull(model.actionError)
    if (errors.isEmpty() || model.alerts.alertVersion == dismissed) return
    val close = { dismissed = model.alerts.alertVersion }
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(close) { Text(model.label("close")) } },
        title = { Text(errors.first()) },
        text = errors.drop(1).takeIf { it.isNotEmpty() }?.let { rest -> { Text(rest.joinToString("\n")) } },
    )
}
