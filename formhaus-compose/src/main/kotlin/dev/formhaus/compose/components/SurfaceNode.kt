package dev.formhaus.compose.components

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.theme.FormhausTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.ceil

internal data class SurfaceStyle(
    val shape: Shape,
    val fill: Color,
    val border: Color,
    val lineWidth: Dp = 1.dp,
    val hoverFill: Color = fill,
    val hoverBorder: Color = border,
    val ring: Color = Color.Unspecified,
    val ringRadius: Dp = 0.dp,
    val ringOffset: Dp = 1.dp,
    val focused: Boolean = false,
    val pressScale: Float = 1f,
)

internal data class MotionSpec(
    val millis: Int,
    val reduced: Boolean,
) {
    fun <T> spec(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(millis, easing = EaseOut)
}

internal val FormhausTokens.quickMotion: MotionSpec get() = MotionSpec(motion.quick, reduceMotion)

internal fun Modifier.animatedSurface(
    style: SurfaceStyle,
    motion: MotionSpec,
    source: InteractionSource? = null,
): Modifier = this then SurfaceElement(style, motion, source)

private data class SurfaceElement(
    val style: SurfaceStyle,
    val motion: MotionSpec,
    val source: InteractionSource?,
) : ModifierNodeElement<SurfaceNode>() {
    override fun create(): SurfaceNode = SurfaceNode(style, motion, source)

    override fun update(node: SurfaceNode) = node.update(style, motion, source)

    override fun InspectorInfo.inspectableProperties() {
        name = "animatedSurface"
    }
}

private class SurfaceNode(
    private var style: SurfaceStyle,
    private var motion: MotionSpec,
    private var source: InteractionSource?,
) : Modifier.Node(),
    DrawModifierNode {
    private val hovers = mutableListOf<HoverInteraction.Enter>()
    private val presses = mutableListOf<PressInteraction.Press>()
    private val focuses = mutableListOf<FocusInteraction.Focus>()
    private val fill = Animatable(style.fill)
    private val border = Animatable(style.border)
    private val ring = Animatable(if (style.focused) 1f else 0f)
    private val scale = Animatable(1f)
    private var watching: Job? = null

    override fun onAttach() {
        watch(snap = true)
    }

    override fun onDetach() {
        watching = null
        clearInteractions()
    }

    fun update(
        style: SurfaceStyle,
        motion: MotionSpec,
        source: InteractionSource?,
    ) {
        this.style = style
        this.motion = motion
        if (source != this.source) {
            this.source = source
            if (isAttached) watch(snap = false)
        }
        if (isAttached) retarget(snap = false)
        invalidateDraw()
    }

    private fun watch(snap: Boolean) {
        watching?.cancel()
        clearInteractions()
        retarget(snap)
        val source = source ?: return
        watching = coroutineScope.launch { source.interactions.collect(::track) }
    }

    private fun clearInteractions() {
        hovers.clear()
        presses.clear()
        focuses.clear()
    }

    private fun track(interaction: Interaction) {
        when (interaction) {
            is HoverInteraction.Enter -> hovers += interaction
            is HoverInteraction.Exit -> hovers -= interaction.enter
            is PressInteraction.Press -> presses += interaction
            is PressInteraction.Release -> presses -= interaction.press
            is PressInteraction.Cancel -> presses -= interaction.press
            is FocusInteraction.Focus -> focuses += interaction
            is FocusInteraction.Unfocus -> focuses -= interaction.focus
        }
        retarget(snap = false)
    }

    private fun retarget(snap: Boolean) {
        val hovered = hovers.isNotEmpty()
        animate(fill, if (hovered) style.hoverFill else style.fill, snap)
        animate(border, if (hovered) style.hoverBorder else style.border, snap)
        animate(ring, if (style.focused || focuses.isNotEmpty()) 1f else 0f, snap)
        animate(scale, if (presses.isNotEmpty() && !motion.reduced) style.pressScale else 1f, snap)
    }

    private fun <T, V : AnimationVector> animate(
        value: Animatable<T, V>,
        target: T,
        snap: Boolean,
    ) {
        if (value.targetValue == target && value.value == target) return
        coroutineScope.launch { if (snap) value.snapTo(target) else value.animateTo(target, motion.spec()) }
    }

    override fun ContentDrawScope.draw() {
        val factor = scale.value
        if (factor == 1f) return drawSurface()
        drawContext.canvas.save()
        drawContext.transform.scale(factor, factor, center)
        drawSurface()
        drawContext.canvas.restore()
    }

    private fun ContentDrawScope.drawSurface() {
        val outline = style.shape.createOutline(size, layoutDirection, this)
        if (fill.value.alpha > 0f) drawOutline(outline, fill.value)
        drawContent()
        if (border.value.alpha > 0f) drawBorder(outline, border.value)
        drawRing()
    }

    private fun DrawScope.drawBorder(
        outline: Outline,
        color: Color,
    ) {
        val stroke = minOf(if (style.lineWidth == Dp.Hairline) 1f else ceil(style.lineWidth.toPx()), ceil(size.minDimension / 2))
        val half = stroke / 2
        val radius = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius ?: CornerRadius.Zero
        when {
            stroke * 2 >= size.minDimension -> {
                drawRoundRect(color, cornerRadius = radius)
            }

            else -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(half, half),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(maxOf(0f, radius.x - half), maxOf(0f, radius.y - half)),
                    style = Stroke(stroke),
                )
            }
        }
    }

    private fun DrawScope.drawRing() {
        val alpha = ring.value
        if (alpha == 0f || style.ring == Color.Unspecified) return
        val inset = (style.ringOffset + 1.dp).toPx()
        drawRoundRect(
            color = style.ring,
            topLeft = Offset(-inset, -inset),
            size = Size(size.width + 2 * inset, size.height + 2 * inset),
            cornerRadius = CornerRadius((style.ringRadius + style.ringOffset).toPx()),
            style = Stroke(2.dp.toPx()),
            alpha = alpha,
        )
    }
}
