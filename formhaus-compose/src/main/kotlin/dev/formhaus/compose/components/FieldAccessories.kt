package dev.formhaus.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.theme.LocalFormhausTokens

@Composable
internal fun FieldAccessories(
    context: FieldContext,
    clearable: Boolean,
    clear: () -> Unit,
) {
    LoadingIndicator(context.loading)
    if (clearable && !context.isRequired && !context.isLocked) {
        IconButton(FormhausIcons.clear, "${context.text("clear")}, ${context.label}", embedded = true, action = clear)
    }
}

@Composable
public fun LoadingIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val tokens = LocalFormhausTokens.current
    CircularProgressIndicator(
        modifier.size(tokens.controls.indicator).clearAndSetSemantics {},
        color = tokens.colors.textMuted,
        strokeWidth = 2.dp,
        trackColor = tokens.colors.backgroundDisabled,
    )
}

@Composable
internal fun IconButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    action: () -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    val side = if (embedded) tokens.controls.iconButton else tokens.controls.hitTarget
    val glyph = tokens.controls.iconButton * 0.5f * (if (embedded) maxOf(1f, tokens.scale) else 1f)
    Box(
        modifier
            .size(side)
            .clickable(role = Role.Button, onClick = action)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(glyph), tint = tokens.colors.textMuted)
    }
}
