package dev.formhaus.compose.fields

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.focusRing
import dev.formhaus.compose.components.formhausFocus
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.renderer.clampedInt
import kotlin.math.floor

private const val POINT_LIMIT = 1_000_000
private const val MAX_POINTS = 11
private const val TEN = 10

public class ScalePoints(
    context: FieldContext,
) {
    public val points: List<Int>
    internal val anchors: List<String?>

    init {
        val range = context.field.scaleRange
        val low = clampedInt(range?.min ?: 1.0, -POINT_LIMIT..POINT_LIMIT) ?: 1
        val high = clampedInt(range?.max ?: 5.0, -POINT_LIMIT..POINT_LIMIT) ?: 5
        val count = minOf(MAX_POINTS, maxOf(0, high - low + 1))
        points = (low until low + count).toList()
        val middle = middle(low, high)
        anchors =
            listOf(low, middle, high).mapIndexed { index, point ->
                if (index == 1 && (point == low || point == high)) null else context.options.firstOrNull { it.value == "$point" }?.label
            }
    }

    public val low: Int get() = points.firstOrNull() ?: 1
    public val high: Int get() = points.lastOrNull() ?: 1

    public fun clamped(point: Int): Int = point.coerceIn(low, maxOf(low, high))

    public fun anchor(point: Int): String? =
        when (point) {
            low -> anchors[0]
            high -> anchors[2]
            middle(low, high) -> anchors[1]
            else -> null
        }

    internal fun point(digit: Int): Int? {
        val point = if (digit == 0 && low > 0) TEN else digit
        return point.takeIf { it in points }
    }

    public fun selected(value: JsonValue?): Int? {
        val number = value?.numberValue ?: return null
        if (floor(number) != number || number < Int.MIN_VALUE || number > Int.MAX_VALUE) return null
        return number.toInt().takeIf { it in points }
    }
}

private fun middle(
    low: Int,
    high: Int,
): Int = floor((low.toDouble() + high) / 2).toInt()

internal fun scaleColumns(
    width: Dp,
    count: Int,
): Int {
    var columns = count
    if (count >= 8 && width < 444.dp) columns = if (count == 8) 4 else 5
    if (count >= TEN && width < 544.dp) columns = if (count == TEN) 5 else 6
    return maxOf(1, columns)
}

@Composable
internal fun ScaleAnchors(
    anchors: List<String?>,
    modifier: Modifier = Modifier,
) {
    if (anchors.all { it == null }) return
    val tokens = LocalFormhausTokens.current
    val style = tokens.textStyle(TextRole.helper)
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s5)) {
        Text(anchors[0].orEmpty(), Modifier.weight(1f), style = style)
        anchors[1]?.let { Text(it, style = style, textAlign = TextAlign.Center) }
        Text(anchors[2].orEmpty(), Modifier.weight(1f), style = style, textAlign = TextAlign.End)
    }
}

@Composable
internal fun Modifier.scaleInput(
    context: FieldContext,
    scale: ScalePoints,
    current: Int?,
): Modifier {
    val tokens = LocalFormhausTokens.current
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    return focusRing(focused, tokens.radii.control, 2.dp)
        .formhausFocus(context, requester)
        .onFocusChanged { focused = it.isFocused }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || context.isLocked || event.hasCommandModifier) return@onKeyEvent false
            val step =
                when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> 0
                }
            if (step != 0) return@onKeyEvent move(scale, current, step) { context.setValue(JsonValue.Number(it.toDouble())) }
            val digit = event.utf16CodePoint.toChar().digitToIntOrNull() ?: return@onKeyEvent false
            val point = scale.point(digit) ?: return@onKeyEvent false
            context.commit(JsonValue.Number(point.toDouble()))
            true
        }.focusable(!context.isLocked)
}

private val KeyEvent.hasCommandModifier: Boolean get() = isCtrlPressed || isAltPressed || isMetaPressed

private inline fun move(
    scale: ScalePoints,
    current: Int?,
    step: Int,
    select: (Int) -> Unit,
): Boolean {
    val next = (current ?: if (step > 0) scale.low - 1 else scale.high + 1) + step
    if (next !in scale.points) return false
    select(next)
    return true
}
