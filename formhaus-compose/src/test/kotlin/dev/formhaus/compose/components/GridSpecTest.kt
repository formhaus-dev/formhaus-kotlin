package dev.formhaus.compose.components

import androidx.compose.ui.unit.dp
import dev.formhaus.compose.fields.scaleColumns
import org.junit.Assert.assertEquals
import org.junit.Test

class GridSpecTest {
    @Test
    fun adaptiveGridsFitTheWidthAndNeverExceedTheItems() {
        val grid = GridSpec(null, 160.dp, 0.dp, 12.dp)
        assertEquals(2, grid.columnCount(360.dp, 5))
        assertEquals(3, grid.columnCount(600.dp, 5))
        assertEquals(2, grid.columnCount(600.dp, 2))
        assertEquals(1, grid.columnCount(100.dp, 4))
    }

    @Test
    fun fixedColumnsShrinkWhenCellsGetTooNarrow() {
        assertEquals(4, GridSpec(4, 0.dp, 0.dp, 12.dp).columnCount(300.dp, 8))
        assertEquals(2, GridSpec(4, 120.dp, 480.dp, 12.dp).columnCount(400.dp, 8))
        assertEquals(3, GridSpec(null, 50.dp, 0.dp, 0.dp, balanced = true).columnCount(200.dp, 5))
    }

    @Test
    fun longScalesWrapIntoRowsOnNarrowScreens() {
        assertEquals(11, scaleColumns(600.dp, 11))
        assertEquals(6, scaleColumns(360.dp, 11))
        assertEquals(5, scaleColumns(360.dp, 10))
        assertEquals(4, scaleColumns(360.dp, 8))
        assertEquals(5, scaleColumns(360.dp, 5))
    }
}
