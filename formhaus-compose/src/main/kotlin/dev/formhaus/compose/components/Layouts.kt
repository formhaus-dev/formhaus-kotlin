package dev.formhaus.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.formhaus.core.renderer.clampedInt

private const val MAX_COLUMNS = 12

internal data class GridSpec(
    val columns: Int?,
    val minimumWidth: Dp,
    val compactWidth: Dp,
    val spacing: Dp,
    val fraction: Float = 0.7f,
    val balanced: Boolean = false,
) {
    fun columnCount(
        width: Dp,
        count: Int,
    ): Int {
        val fitted = clampedInt(((width + spacing) / maxOf(1.dp, minimumWidth + spacing)).toDouble(), 1..MAX_COLUMNS) ?: 1
        val chosen = columns ?: fitted
        val limited = if (width < compactWidth && columns != 1) minOf(chosen, 2) else chosen
        var result = maxOf(1, minOf(limited, maxOf(1, count)))
        while (result > 1 && cellWidth(width, result) < minimumWidth * fraction) result -= 1
        while (balanced && result in 3 until count && count % result != 0 && count % result * 2 < result) result -= 1
        return result
    }

    private fun cellWidth(
        width: Dp,
        count: Int,
    ): Dp = maxOf(0.dp, (width - spacing * (count - 1)) / count)
}

@Composable
internal fun ColumnGrid(
    spacing: Dp,
    columnCount: (width: Dp, count: Int) -> Int,
    modifier: Modifier = Modifier,
    fallbackWidth: Dp = 440.dp,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else fallbackWidth.roundToPx()
        val gap = spacing.roundToPx()
        val count = maxOf(1, columnCount(width.toDp(), measurables.size))
        val cell = maxOf(0, (width - gap * (count - 1)) / count)
        val rows = measurables.chunked(count)
        val heights = rows.map { row -> row.maxOf { it.maxIntrinsicHeight(cell) } }
        val placed = rows.mapIndexed { index, row -> row.map { it.measure(Constraints.fixed(cell, heights[index])) } }
        val height = heights.sum() + gap * maxOf(0, rows.size - 1)
        layout(width, height) {
            var y = 0
            placed.forEachIndexed { index, row ->
                row.forEachIndexed { column, placeable -> placeable.place(column * (cell + gap), y) }
                y += heights[index] + gap
            }
        }
    }
}
