package dev.formhaus.compose.theme

import androidx.compose.ui.unit.dp
import dev.formhaus.core.json.ValueParts
import dev.formhaus.core.json.describe
import dev.formhaus.core.json.equalParts
import dev.formhaus.core.json.partsHash

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

public class Motion(
    public val quick: Int = 140,
    public val standard: Int = 150,
    public val progress: Int = 200,
) {
    public fun copy(
        quick: Int = this.quick,
        standard: Int = this.standard,
        progress: Int = this.progress,
    ): Motion = Motion(quick, standard, progress)

    override fun equals(other: Any?): Boolean = equalParts(other, Motion::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("Motion")

    private fun parts(): ValueParts = listOf("quick" to quick, "standard" to standard, "progress" to progress)
}
