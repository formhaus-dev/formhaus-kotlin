package dev.formhaus.compose.fields

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import dev.formhaus.compose.components.ColumnGrid
import dev.formhaus.compose.components.FieldShell
import dev.formhaus.compose.components.FieldShellVariant
import dev.formhaus.compose.components.selectableSurface
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.model.formhausTestId
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.json.JsonValue

@Composable
public fun ScaleField(context: FieldContext) {
    val tokens = LocalFormhausTokens.current
    val scale = ScalePoints(context)
    val current = scale.selected(context.value)
    val focus = remember { ScaleFocus() }
    FieldShell(context, variant = FieldShellVariant.GROUP) {
        Column(Modifier.scaleInput(context, scale, current, focus), verticalArrangement = Arrangement.spacedBy(tokens.spacing.s2)) {
            ColumnGrid(tokens.spacing.s2, ::scaleColumns, Modifier.scaleRing(focus).selectableGroup()) {
                for (point in scale.points) {
                    ScaleBox(
                        "$point",
                        current == point,
                        !context.isLocked,
                        context.isInvalid,
                        pointDescription(point, scale.anchor(point)),
                        Modifier.formhausTestId(context.optionTestId("$point")),
                    ) { context.commit(JsonValue.Number(point.toDouble())) }
                }
            }
            ScaleAnchors(scale.anchors)
        }
    }
}

internal fun pointDescription(
    point: Int,
    anchor: String?,
): String = if (anchor.isNullOrEmpty()) "$point" else "$point, $anchor"

@Composable
private fun ScaleBox(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    invalid: Boolean,
    description: String,
    modifier: Modifier,
    choose: () -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = maxOf(tokens.controls.height, tokens.controls.hitTarget))
            .selectableSurface(selected, invalid, solid = true, enabled = enabled, source = source)
            .selectable(selected, source, null, enabled, Role.RadioButton, choose)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) tokens.colors.onPrimary else tokens.colors.text,
            style = tokens.textStyle(TextRole.label.copy(weight = FontWeight.Medium, tabularDigits = true), colored = false),
        )
    }
}
