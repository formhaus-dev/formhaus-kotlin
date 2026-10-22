package dev.formhaus.compose.containers

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.form.FormNodeView
import dev.formhaus.compose.form.LocalFormComponents
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.options
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.renderer.nodeId

private const val COLUMN_WIDTH = 96
private const val LABEL_SHARE = 1.5f
private const val LABEL_MINIMUM = 160

@Composable
internal fun MatrixRows(
    fields: List<FormNode>,
    model: FormModel,
) {
    val tokens = LocalFormhausTokens.current
    val builtIns = LocalFormComponents.current.builtIns
    val first = fields.firstOrNull() as? FormField
    val columns = first?.let { model.options(it.key) ?: it.options }.orEmpty()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val needed = maxOf(tokens.controls.matrixWidth, (COLUMN_WIDTH * (columns.size + 2) * maxOf(1f, tokens.scale)).dp)
        val wide = maxWidth >= needed
        Column(Modifier.fillMaxWidth()) {
            if (wide) MatrixHeader(columns)
            fields.forEachIndexed { index, node ->
                key(node.nodeId) {
                    if (node is FormField && builtIns && (node.type == "radio" || node.type == "multiselect")) {
                        MatrixRow(node, model, wide, index == fields.lastIndex)
                    } else {
                        Column(Modifier.padding(vertical = tokens.spacing.s5)) { FormNodeView(node, model) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixHeader(columns: List<FieldOption>) {
    val tokens = LocalFormhausTokens.current
    Column(Modifier.clearAndSetSemantics {}) {
        MatrixLine(columns.size, Modifier.padding(vertical = tokens.spacing.s3)) {
            Spacer(Modifier.height(1.dp))
            columns.forEach { option ->
                Text(
                    option.label.orEmpty(),
                    Modifier.wrapContentHeight(Alignment.Bottom),
                    textAlign = TextAlign.Center,
                    style = tokens.textStyle(TextRole.caption),
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = tokens.colors.border)
    }
}

@Composable
internal fun MatrixLine(
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else fallbackWidth.roundToPx()
        val columns = maxOf(1, count)
        val label = maxOf(LABEL_MINIMUM.dp.roundToPx(), (width * LABEL_SHARE / (LABEL_SHARE + columns)).toInt())
        val cell = maxOf(0, (width - label) / columns)
        val widths = measurables.indices.map { if (it == 0) label else cell }
        val height = measurables.mapIndexed { index, cellMeasurable -> cellMeasurable.maxIntrinsicHeight(widths[index]) }.maxOrNull() ?: 0
        val placeables =
            measurables.mapIndexed {
                index,
                cellMeasurable,
                ->
                cellMeasurable.measure(Constraints.fixed(widths[index], height))
            }
        layout(width, height) {
            placeables.forEachIndexed { index, placeable -> placeable.place(if (index == 0) 0 else label + (index - 1) * cell, 0) }
        }
    }
}

private val fallbackWidth = 560.dp
