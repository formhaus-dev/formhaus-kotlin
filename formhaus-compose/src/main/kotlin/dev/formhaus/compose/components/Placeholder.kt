package dev.formhaus.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle

@Composable
internal fun Placeholder(
    text: String?,
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    Box {
        if (visible && !text.isNullOrEmpty()) {
            Text(
                text,
                Modifier.clearAndSetSemantics {},
                color = tokens.colors.control,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tokens.textStyle(TextRole.body, colored = false),
            )
        }
        content()
    }
}
