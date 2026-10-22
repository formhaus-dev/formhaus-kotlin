package dev.formhaus.demo.kiln

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.FormhausOptionImage
import dev.formhaus.compose.components.formhausFocus
import dev.formhaus.compose.fields.ChoiceSelection
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.model.FieldOption
import dev.formhaus.demo.DemoIcons

private val pictureMinWidth = 150.dp
private val pictureSpacing = 12.dp
private const val CLAY_RED = 0xF2 / 255f
private const val CLAY_GREEN = 0xD3 / 255f
private const val CLAY_BLUE = 0xBD / 255f

private const val DARK_BOOST = 1.8f

private fun clay(boost: Float): ColorFilter =
    ColorFilter.colorMatrix(
        ColorMatrix().apply {
            setToScale(CLAY_RED * boost, CLAY_GREEN * boost, CLAY_BLUE * boost, 1f)
            timesAssign(ColorMatrix().apply { setToSaturation(0f) })
        },
    )

private val clayLight = clay(1f)
private val clayDark = clay(DARK_BOOST)

@Composable
internal fun KilnChoiceField(context: FieldContext) {
    val requester = remember { FocusRequester() }
    val selection = ChoiceSelection(context)
    KilnFieldFrame(context) {
        val focus = Modifier.formhausFocus(context, requester)
        if (context.options.any { !it.image.isNullOrEmpty() }) {
            KilnPictureGrid(context.options, focus) { option -> KilnPictureCard(option, selection, context) }
        } else {
            FlowRow(focus, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in context.options) {
                    val value = option.value.orEmpty()
                    KilnChip(
                        option.label ?: value,
                        selection.isSelected(value),
                        context.isInvalid,
                        Modifier.clip(CircleShape).choice(value, selection, context),
                    )
                }
            }
        }
    }
}

@Composable
internal fun KilnChip(
    label: String,
    selected: Boolean,
    invalid: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFormhausTokens.current.colors
    val border =
        when {
            selected -> Color.Transparent
            invalid -> colors.error
            else -> colors.secondaryBorder
        }
    Row(
        modifier
            .heightIn(min = 44.dp)
            .background(if (selected) colors.primary else colors.backgroundDisabled, CircleShape)
            .border(if (invalid && !selected) 1.5.dp else 1.dp, border, CircleShape)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (selected) colors.onPrimary else colors.text
        if (selected) Icon(DemoIcons.check, null, Modifier.size(18.dp), tint = tint)
        Text(label, color = tint, style = KilnType.body.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun KilnPictureGrid(
    options: List<FieldOption>,
    modifier: Modifier,
    card: @Composable (FieldOption) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val columns = maxOf(1, ((maxWidth + pictureSpacing) / (pictureMinWidth + pictureSpacing)).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(pictureSpacing)) {
            for (row in options.chunked(columns)) {
                Row(horizontalArrangement = Arrangement.spacedBy(pictureSpacing)) {
                    for (option in row) Box(Modifier.weight(1f)) { card(option) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun KilnPictureCard(
    option: FieldOption,
    selection: ChoiceSelection,
    context: FieldContext,
) {
    val colors = LocalFormhausTokens.current.colors
    val value = option.value.orEmpty()
    val selected = selection.isSelected(value)
    val shape = RoundedCornerShape(20.dp)
    val tint = if (isSystemInDarkTheme()) clayDark else clayLight
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) colors.primarySoft else colors.backgroundDisabled)
            .border(2.dp, if (selected) colors.primary else Color.Transparent, shape)
            .choice(value, selection, context)
            .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FormhausOptionImage(
                option,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.border)
                    .drawWithContent {
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint().apply { colorFilter = tint })
                            drawContent()
                            canvas.restore()
                        }
                    },
            )
            Text(option.label ?: value, Modifier.padding(horizontal = 4.dp), color = colors.text, style = KilnType.heading)
        }
        if (selected) {
            Icon(
                DemoIcons.check,
                null,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .background(colors.primary, CircleShape)
                    .padding(5.dp),
                tint = colors.onPrimary,
            )
        }
    }
}

private fun Modifier.choice(
    value: String,
    selection: ChoiceSelection,
    context: FieldContext,
): Modifier {
    val selected = selection.isSelected(value)
    val enabled = !context.isLocked
    return if (selection.multiple) {
        toggleable(selected, enabled = enabled, role = Role.Checkbox) { selection.choose(value) }
    } else {
        selectable(selected, enabled = enabled, role = Role.RadioButton) { selection.choose(value) }
    }
}
