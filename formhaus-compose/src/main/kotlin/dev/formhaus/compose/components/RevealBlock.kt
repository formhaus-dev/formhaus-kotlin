package dev.formhaus.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import dev.formhaus.compose.theme.LocalFormhausTokens

@Composable
internal fun RevealBlock(
    inset: Dp,
    line: Color,
    content: @Composable () -> Unit,
) {
    val spacing = LocalFormhausTokens.current.spacing
    Box(
        Modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true }
            .padding(start = inset)
            .drawBehind {
                val width = spacing.s1.toPx()
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                drawRect(line, Offset(x, 0f), Size(width, size.height))
            }.padding(start = spacing.s1 + spacing.s10, top = spacing.s3, bottom = spacing.s3),
    ) { content() }
}
