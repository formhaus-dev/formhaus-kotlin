package dev.formhaus.compose.containers

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.FormButton
import dev.formhaus.compose.components.FormButtonVariant
import dev.formhaus.compose.components.measured
import dev.formhaus.compose.form.ActionsContext
import dev.formhaus.compose.form.BackPlacement
import dev.formhaus.compose.form.LocalFormBehavior
import dev.formhaus.compose.form.LocalFormComponents
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.formhausTestId
import dev.formhaus.compose.theme.LocalFormhausTokens

@Composable
internal fun FormActionsSlot(model: FormModel) {
    val context = ActionsContext(model)
    val custom = LocalFormComponents.current.slots.actions
    if (custom != null) custom(context) else FormActionsBar(context)
    StepBackHandler(context, LocalFormBehavior.current.backPlacement != BackPlacement.BOTTOM && custom == null)
}

@Composable
internal fun StepBackHandler(
    context: ActionsContext,
    automatic: Boolean,
) {
    if (!automatic || LocalOnBackPressedDispatcherOwner.current == null) return
    BackHandler(context.state.showBack && !context.busy) { context.back() }
}

internal val ActionsContext.showsBackButton: Boolean
    @Composable get() = state.showBack && LocalFormBehavior.current.backPlacement != BackPlacement.APP_BAR

@Composable
public fun FormActionsBar(
    context: ActionsContext,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFormhausTokens.current
    val forced = LocalActionsLayout.current
    BoxWithConstraints(modifier.fillMaxWidth().padding(top = tokens.spacing.s1)) {
        val layout = forced ?: if (maxWidth < tokens.controls.compactWidth) ActionsLayout.STACKED else ActionsLayout.ROW
        if (layout ==
            ActionsLayout.STACKED
        ) {
            StackedActions(context)
        } else {
            FirstThatFits({ RowActions(context) }, { StackedActions(context) })
        }
    }
}

@Composable
private fun RowActions(context: ActionsContext) {
    val tokens = LocalFormhausTokens.current
    val state = context.state
    val first =
        when {
            context.showsBackButton -> FormButtonVariant.of(state.backAction, FormButtonVariant.SECONDARY)
            state.cancelAction != null -> FormButtonVariant.of(state.cancelAction, FormButtonVariant.TEXT)
            state.showSkip -> FormButtonVariant.of(state.skipAction, FormButtonVariant.TEXT)
            else -> null
        }
    Row(
        Modifier.outdent(if (first == FormButtonVariant.TEXT) tokens.spacing.s4 else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(context, Modifier)
        TextButtons(context)
        Spacer(Modifier.weight(1f).widthIn(min = tokens.spacing.s3))
        PrimaryButton(context, Modifier)
    }
}

@Composable
private fun StackedActions(context: ActionsContext) {
    val tokens = LocalFormhausTokens.current
    val state = context.state
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(tokens.spacing.s3)) {
        PrimaryButton(context, Modifier.fillMaxWidth())
        BackButton(context, Modifier.fillMaxWidth())
        if (state.cancelAction != null || state.showSkip) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s3, Alignment.CenterHorizontally),
            ) { TextButtons(context) }
        }
    }
}

@Composable
private fun BackButton(
    context: ActionsContext,
    modifier: Modifier,
) {
    val state = context.state
    if (!context.showsBackButton) return
    FormButton(
        state.backLabel,
        FormButtonVariant.of(state.backAction, FormButtonVariant.SECONDARY),
        context::back,
        modifier.formhausTestId(context.testIds.back).measured("actions/back"),
        enabled = !context.busy,
    )
}

@Composable
private fun TextButtons(context: ActionsContext) {
    val state = context.state
    state.cancelAction?.let { cancel ->
        FormButton(
            cancel.label.orEmpty(),
            FormButtonVariant.of(cancel, FormButtonVariant.TEXT),
            context::cancel,
            Modifier.formhausTestId(context.testIds.cancel),
            enabled = !context.busy,
        )
    }
    if (state.showSkip) {
        FormButton(state.skipLabel, FormButtonVariant.of(state.skipAction, FormButtonVariant.TEXT), context::skip, enabled = !context.busy)
    }
}

@Composable
private fun PrimaryButton(
    context: ActionsContext,
    modifier: Modifier,
) {
    val state = context.state
    if (!state.showPrimary) return
    FormButton(
        state.primaryLabel,
        FormButtonVariant.PRIMARY,
        context::performPrimaryAction,
        modifier.formhausTestId(context.primaryTestId).measured("actions/primary"),
        enabled = !context.busy && !state.primaryDisabled,
        busy = context.busy && !context.model.loading,
    )
}

private fun Modifier.outdent(start: Dp): Modifier =
    layout { measurable, constraints ->
        val inset = start.roundToPx()
        val widened = if (constraints.hasBoundedWidth) constraints.copy(maxWidth = constraints.maxWidth + inset) else constraints
        val placeable = measurable.measure(widened.copy(minWidth = minOf(widened.maxWidth, constraints.minWidth + inset)))
        layout(placeable.width - inset, placeable.height) { placeable.place(-inset, 0) }
    }

@Composable
private fun FirstThatFits(
    first: @Composable () -> Unit,
    fallback: @Composable () -> Unit,
) {
    SubcomposeLayout { constraints ->
        val wide = subcompose(true, first)
        val fits = wide.sumOf { it.maxIntrinsicWidth(Constraints.Infinity) } <= constraints.maxWidth
        val chosen = if (fits) wide else subcompose(false, fallback)
        val placeables = chosen.map { it.measure(constraints.copy(minHeight = 0)) }
        layout(constraints.maxWidth, placeables.maxOfOrNull { it.height } ?: 0) { placeables.forEach { it.place(0, 0) } }
    }
}
