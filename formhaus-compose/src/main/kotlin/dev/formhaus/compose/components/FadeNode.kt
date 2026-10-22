package dev.formhaus.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.launch

internal fun Modifier.fade(
    visible: Boolean,
    motion: MotionSpec,
): Modifier = this then FadeElement(visible, motion)

private data class FadeElement(
    val visible: Boolean,
    val motion: MotionSpec,
) : ModifierNodeElement<FadeNode>() {
    override fun create(): FadeNode = FadeNode(visible, motion)

    override fun update(node: FadeNode) = node.update(visible, motion)

    override fun InspectorInfo.inspectableProperties() {
        name = "fade"
    }
}

private class FadeNode(
    visible: Boolean,
    private var motion: MotionSpec,
) : Modifier.Node(),
    LayoutModifierNode {
    private var target = if (visible) 1f else 0f
    private val alpha = Animatable(target)

    override fun onAttach() {
        if (alpha.value != target) coroutineScope.launch { alpha.snapTo(target) }
    }

    fun update(
        visible: Boolean,
        motion: MotionSpec,
    ) {
        this.motion = motion
        target = if (visible) 1f else 0f
        if (isAttached && alpha.targetValue != target) coroutineScope.launch { alpha.animateTo(target, motion.spec()) }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0) { alpha = this@FadeNode.alpha.value } }
    }
}
