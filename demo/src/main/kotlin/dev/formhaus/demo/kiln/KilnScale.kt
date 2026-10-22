package dev.formhaus.demo.kiln

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.formhausFocus
import dev.formhaus.compose.fields.ScalePoints
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.json.JsonValue
import dev.formhaus.demo.DemoIcons

@Composable
internal fun KilnScaleField(context: FieldContext) {
    val colors = LocalFormhausTokens.current.colors
    val scale = ScalePoints(context)
    val current = scale.selected(context.value)
    KilnScaleFrame(context, scale, footer = { KilnAnchors(scale) }) { point, modifier ->
        val selected = point == current
        Box(
            modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .background(if (selected) colors.primary else colors.backgroundDisabled, CircleShape)
                .border(1.5.dp, if (context.isInvalid) colors.error else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$point",
                color = if (selected) colors.onPrimary else colors.text,
                style = KilnType.body.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun KilnAnchors(scale: ScalePoints) {
    val low = scale.anchor(scale.low)
    val high = scale.anchor(scale.high)
    if (low == null && high == null) return
    val muted = LocalFormhausTokens.current.colors.textMuted
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(low.orEmpty(), color = muted, style = KilnType.note)
        Text(high.orEmpty(), color = muted, style = KilnType.note)
    }
}

@Composable
internal fun KilnRatingField(context: FieldContext) {
    val colors = LocalFormhausTokens.current.colors
    val scale = ScalePoints(context)
    val current = scale.selected(context.value) ?: 0
    KilnScaleFrame(context, scale, Modifier.offset(x = (-8).dp), spacing = 4) { point, modifier ->
        val filled = point <= current
        Box(modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(
                if (filled) DemoIcons.favoriteFilled else DemoIcons.favorite,
                null,
                Modifier.size(28.dp),
                tint = if (filled) colors.primary else colors.control,
            )
        }
    }
}

@Composable
private fun KilnScaleFrame(
    context: FieldContext,
    scale: ScalePoints,
    modifier: Modifier = Modifier,
    spacing: Int = 6,
    footer: @Composable () -> Unit = {},
    point: @Composable RowScope.(Int, Modifier) -> Unit,
) {
    val requester = remember { FocusRequester() }
    val current = scale.selected(context.value)
    KilnFieldFrame(context) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier.formhausFocus(context, requester).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(spacing.dp),
            ) {
                for (value in scale.points) {
                    val tap =
                        Modifier
                            .clip(CircleShape)
                            .selectable(value == current, enabled = !context.isLocked, role = Role.RadioButton) {
                                context.commit(JsonValue.Number(value.toDouble()))
                            }.semantics { contentDescription = "$value" }
                    point(value, tap)
                }
            }
            footer()
        }
    }
}
