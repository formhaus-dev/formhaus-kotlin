package dev.formhaus.compose.fields

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.formhaus.compose.components.FieldShell
import dev.formhaus.compose.components.FieldShellVariant
import dev.formhaus.compose.components.FormhausIcons
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.model.formhausTestId
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.json.JsonValue

private const val STAR_REACH = 1.4f
private const val GLYPH = 0.8f

@Composable
public fun RatingField(context: FieldContext) {
    val tokens = LocalFormhausTokens.current
    val scale = ScalePoints(context)
    val current = scale.selected(context.value)
    val sources = remember(scale.points) { scale.points.map { MutableInteractionSource() } }
    val hovers = sources.map { it.collectIsHoveredAsState() }
    val hovered = hovers.indexOfFirst { it.value }.takeIf { it >= 0 }?.let { scale.points[it] }
    val shown = hovered ?: current ?: 0
    val width = minOf(tokens.controls.star, tokens.controls.hitTarget * STAR_REACH) + tokens.spacing.s1
    FieldShell(context, variant = FieldShellVariant.GROUP) {
        Column(Modifier.scaleInput(context, scale, current), verticalArrangement = Arrangement.spacedBy(tokens.spacing.s2)) {
            Row(Modifier.offset(x = -tokens.spacing.s1 / 2).selectableGroup()) {
                scale.points.forEachIndexed { index, point ->
                    Star(
                        point <= shown,
                        hovered != null,
                        current == point,
                        !context.isLocked,
                        width,
                        sources[index],
                        pointDescription(point, scale.anchor(point)),
                        Modifier.formhausTestId(context.optionTestId("$point")),
                    ) { context.commit(JsonValue.Number(point.toDouble())) }
                }
            }
            ScaleAnchors(scale.anchors, Modifier.width(width * scale.points.size))
        }
    }
}

@Composable
private fun Star(
    filled: Boolean,
    hovering: Boolean,
    selected: Boolean,
    enabled: Boolean,
    width: Dp,
    source: MutableInteractionSource,
    description: String,
    modifier: Modifier,
    choose: () -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    val colors = tokens.colors
    val tint =
        when {
            !filled -> colors.control
            hovering -> colors.primaryHover
            else -> colors.primary
        }
    Box(
        modifier
            .size(width, maxOf(tokens.controls.hitTarget, width))
            .selectable(selected, source, null, enabled, Role.RadioButton, choose)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (filled) FormhausIcons.starFilled else FormhausIcons.star,
            null,
            Modifier.size((width - tokens.spacing.s1) * GLYPH),
            tint = tint,
        )
    }
}
