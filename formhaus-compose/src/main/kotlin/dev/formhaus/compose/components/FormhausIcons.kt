package dev.formhaus.compose.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val VIEWPORT = 24f
private const val CIRCLE = "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0"
private const val STAR =
    "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 " +
        "3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 " +
        "21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 " +
        "2.122 0 0 0 1.597-1.16z"

private fun lucide(
    name: String,
    vararg paths: String,
    filled: Boolean = false,
    stroke: Float = 2f,
): ImageVector =
    ImageVector
        .Builder(name, VIEWPORT.dp, VIEWPORT.dp, VIEWPORT, VIEWPORT)
        .apply {
            for (path in paths) {
                addPath(
                    addPathNodes(path),
                    fill = if (filled) SolidColor(Color.Black) else null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

internal object FormhausIcons {
    val check: ImageVector by lazy { lucide("check", "M20 6 9 17l-5-5", stroke = 3.5f) }
    val clear: ImageVector by lazy { lucide("circle-x", CIRCLE, "m15 9-6 6", "m9 9 6 6") }
    val close: ImageVector by lazy { lucide("x", "M18 6 6 18", "m6 6 12 12") }
    val star: ImageVector by lazy { lucide("star", STAR, stroke = 1.5f) }
    val starFilled: ImageVector by lazy { lucide("star-filled", STAR, filled = true, stroke = 1.5f) }
    val calendar: ImageVector by lazy {
        lucide("calendar", "M8 2v4", "M16 2v4", "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M3 10h18")
    }
    val clock: ImageVector by lazy { lucide("clock", CIRCLE, "M12 6v6l4 2") }
    val paperclip: ImageVector by lazy {
        lucide(
            "paperclip",
            "m16 6-8.414 8.586a2 2 0 0 0 2.829 2.829l8.414-8.586a4 4 0 1 0-5.657-5.657l-8.379 8.551a6 6 0 1 0 8.485 8.485l8.379-8.551",
        )
    }
    val chevronsUpDown: ImageVector by lazy { lucide("chevrons-up-down", "m7 15 5 5 5-5", "m7 9 5-5 5 5", stroke = 2.5f) }
    val maximize: ImageVector by lazy { lucide("maximize-2", "M15 3h6v6", "m21 3-7 7", "m3 21 7-7", "M9 21H3v-6", stroke = 2.5f) }
}
