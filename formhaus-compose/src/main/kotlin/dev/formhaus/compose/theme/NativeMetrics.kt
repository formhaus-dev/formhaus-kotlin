package dev.formhaus.compose.theme

import androidx.compose.ui.unit.dp

internal object NativeMetrics {
    val hitTarget = 48.dp
    val indicator = 18.dp
    val indicatorRadius = 5.dp
    val textareaHeight = 112.dp
    val progressHeight = 4.dp
    val maxWidth = 640.dp
    val compactWidth = 480.dp
    val matrixWidth = 560.dp
}

public data class Motion(
    val quick: Int = 140,
    val standard: Int = 150,
    val progress: Int = 200,
)
