package dev.formhaus.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import android.graphics.Paint as FrameworkPaint

@Composable
internal fun ZoomButton(
    label: String,
    modifier: Modifier = Modifier,
    action: () -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    val side = tokens.controls.iconButton
    Box(
        modifier
            .size(tokens.controls.hitTarget)
            .clickable(role = Role.Button, onClick = action)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(side)
                .softShadow(SHADOW, tokens.radii.control - tokens.spacing.s1, tokens.colors.shadow, tokens.colors.background)
                .background(tokens.colors.background, tokens.innerShape)
                .border(1.dp, tokens.colors.border, tokens.innerShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(FormhausIcons.maximize, null, Modifier.size(side * 0.44f), tint = tokens.colors.text)
        }
    }
}

@Composable
internal fun ImageViewer(
    title: String,
    source: String,
    closeLabel: String,
    dismiss: () -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    Dialog(onDismissRequest = dismiss) {
        Column(
            Modifier
                .widthIn(min = 320.dp, max = 560.dp)
                .background(tokens.colors.background, tokens.controlShape)
                .semantics { paneTitle = title },
        ) {
            Row(
                Modifier.padding(start = tokens.spacing.s9, end = tokens.spacing.s5, top = tokens.spacing.s7, bottom = tokens.spacing.s7),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s5),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, Modifier.weight(1f), style = tokens.textStyle(TextRole.heading))
                IconButton(FormhausIcons.close, closeLabel, action = dismiss)
            }
            OptionImage(
                source,
                Modifier.padding(start = tokens.spacing.s9, end = tokens.spacing.s9, bottom = tokens.spacing.s9),
                fit = true,
            )
        }
    }
}

private val SHADOW = 4.dp

private fun Modifier.softShadow(
    blur: Dp,
    radius: Dp,
    color: Color,
    fill: Color,
): Modifier =
    drawWithCache {
        val corner = maxOf(0f, radius.toPx())
        val paint =
            FrameworkPaint().apply {
                isAntiAlias = true
                this.color = fill.toArgb()
                setShadowLayer(blur.toPx(), 0f, blur.toPx() / 2, color.toArgb())
            }
        onDrawBehind { drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, corner, corner, paint) } }
    }
