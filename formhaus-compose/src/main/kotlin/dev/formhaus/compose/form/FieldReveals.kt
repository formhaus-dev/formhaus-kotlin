package dev.formhaus.compose.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import dev.formhaus.compose.components.RevealBlock
import dev.formhaus.compose.fields.drawsReveals
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.RevealLayout
import dev.formhaus.compose.model.revealLayout
import dev.formhaus.core.model.FormNode

@Immutable
internal data class FieldReveals(
    val slots: Map<String, List<FormNode>>,
    val content: @Composable (List<FormNode>) -> Unit,
) {
    @Composable
    fun Block(
        value: String,
        inset: Dp,
        line: Color,
    ) {
        val nodes = slots[value]
        if (!nodes.isNullOrEmpty()) RevealBlock(inset, line) { content(nodes) }
    }
}

internal val LocalFieldReveals: ProvidableCompositionLocal<FieldReveals?> = compositionLocalOf { null }

@Composable
internal fun rememberRevealLayout(
    nodes: List<FormNode>,
    model: FormModel,
): RevealLayout {
    val components = LocalFormComponents.current
    val behavior = LocalFormBehavior.current
    return remember(nodes, model.definition, components, behavior) {
        val limit = behavior.inlineChoiceLimit
        model.revealLayout(nodes, if (behavior.conditionalReveal) { field -> field.drawsReveals(components, limit) } else null)
    }
}

@Composable
internal fun ProvideReveals(
    node: FormNode,
    layout: RevealLayout,
    children: @Composable (List<FormNode>) -> Unit,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFieldReveals provides layout.slots(node)?.let { FieldReveals(it, children) }, content)
}
