package dev.formhaus.compose.containers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import dev.formhaus.compose.form.LocalFormBehavior
import dev.formhaus.compose.form.LocalFormComponents
import dev.formhaus.compose.form.ProgressContext
import dev.formhaus.compose.form.StepStatus
import dev.formhaus.compose.model.FocusRequest
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.FormStructure
import dev.formhaus.compose.model.texts
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf

private const val LARGE_TYPE = 1.3f

@Composable
internal fun FormProgress(model: FormModel) {
    val structure = model.structure
    if (!structure.isMultiStep) return
    val marks = LocalFormBehavior.current.stepErrorMarks
    val step = structure.currentStep
    val texts = step?.let { model.texts(it) }
    val errorCount = step?.let { model.stepErrorCounts[it.id] } ?: 0
    val progress = structure.progress
    val context =
        ProgressContext(
            progress.current,
            progress.total,
            model.label(
                "stepOf",
                stringMapOf(
                    "current" to JsonValue.Number(progress.current.toDouble()),
                    "total" to JsonValue.Number(progress.total.toDouble()),
                ),
            ),
            texts?.title,
            texts?.description,
            if (marks) model.stepMarks(structure) else null,
            errorCount,
            model.errorLabel(errorCount),
        )
    LocalFormComponents.current.slots.progress
        ?.invoke(context) ?: StepProgressView(context, focusRequest = model.focusRequest)
}

private fun FormModel.stepMarks(structure: FormStructure): List<StepStatus> =
    structure.steps.mapIndexed { index, step ->
        val count = stepErrorCounts[step.id] ?: 0
        StepStatus(
            step.id,
            texts(step).title ?: label("stepTitle", stringMapOf("index" to JsonValue.Number(index + 1.0))),
            count,
            errorLabel(count),
        )
    }

private fun FormModel.errorLabel(count: Int): String? =
    if (count > 0) label("stepErrors", stringMapOf("count" to JsonValue.Number(count.toDouble()))) else null

@Composable
public fun StepProgressView(
    context: ProgressContext,
    modifier: Modifier = Modifier,
    focusRequest: FocusRequest? = null,
) {
    val tokens = LocalFormhausTokens.current
    val colors = tokens.colors
    val title = context.stepTitle?.takeIf { it.isNotEmpty() }
    val requester = remember { FocusRequester() }
    LaunchedEffect(focusRequest) {
        if (title == null || focusRequest == null || focusRequest.key != null) return@LaunchedEffect
        withFrameNanos {}
        requester.requestFocus()
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(tokens.spacing.s2)) {
        ProgressBar(if (context.total > 0) context.current.toFloat() / context.total else 0f)
        Text(context.counterLabel, style = tokens.textStyle(TextRole.counter))
        context.steps?.let { steps ->
            val current = steps.getOrNull(context.current - 1)
            if (tokens.scale > LARGE_TYPE && current != null) {
                StepMark(current, true)
            } else {
                StepMarks(steps, context.current)
            }
        }
        if (title != null) {
            Text(
                title,
                Modifier
                    .padding(top = tokens.spacing.s1)
                    .focusRequester(requester)
                    .focusable()
                    .semantics {
                        heading()
                        context.errorLabel?.let { stateDescription = it }
                    },
                color = colors.text,
                style = tokens.textStyle(TextRole.title, colored = false),
            )
        }
        context.stepDescription?.takeIf { it.isNotEmpty() }?.let {
            Text(it, color = colors.textMuted, style = tokens.textStyle(TextRole.body, colored = false))
        }
    }
}

@Composable
private fun StepMarks(
    steps: List<StepStatus>,
    current: Int,
) {
    val spacing = LocalFormhausTokens.current.spacing
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.s7),
        verticalArrangement = Arrangement.spacedBy(spacing.s3),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, step -> StepMark(step, index + 1 == current) }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    val tokens = LocalFormhausTokens.current
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), tokens.animation(tokens.motion.progress), label = "progress")
    Box(
        Modifier
            .fillMaxWidth()
            .height(tokens.controls.progressHeight)
            .clip(CircleShape)
            .background(tokens.colors.border)
            .clearAndSetSemantics {},
    ) {
        Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(tokens.colors.primary, CircleShape))
    }
}

@Composable
private fun StepMark(
    step: StepStatus,
    current: Boolean,
) {
    val tokens = LocalFormhausTokens.current
    val colors = tokens.colors
    val color =
        when {
            step.errorCount > 0 -> colors.error
            current -> colors.text
            else -> colors.textMuted
        }
    val style = tokens.textStyle(TextRole.counter, colored = false).copy(color = color)
    Row(
        Modifier.semantics(mergeDescendants = true) {
            selected = current
            step.errorLabel?.let { stateDescription = it }
        },
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            step.title,
            Modifier.weight(1f, fill = false),
            style = style.copy(fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal),
        )
        if (step.errorCount > 0) {
            Text(
                "${step.errorCount}",
                Modifier
                    .clearAndSetSemantics {}
                    .background(colors.error, CircleShape)
                    .padding(horizontal = tokens.spacing.s2),
                color = colors.background,
                style = style.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
