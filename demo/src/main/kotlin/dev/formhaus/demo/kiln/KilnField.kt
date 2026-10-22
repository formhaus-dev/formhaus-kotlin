package dev.formhaus.demo.kiln

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.form.formhausScrollAnchor
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.demo.DemoIcons
import dev.formhaus.demo.UnderlineInput

@Composable
internal fun KilnFieldFrame(
    context: FieldContext,
    content: @Composable () -> Unit,
) {
    val colors = LocalFormhausTokens.current.colors
    val error = context.error?.takeIf { it.isNotEmpty() }
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (context.isLocked) 0.6f else 1f)
            .semantics { if (error != null) error(error) }
            .formhausScrollAnchor(context),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.clearAndSetSemantics {
                contentDescription = context.accessibilityName
                heading()
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(context.label, Modifier.alignByBaseline(), color = colors.text, style = KilnType.label)
            if (context.isRequired) {
                Text(context.text("required"), Modifier.alignByBaseline(), color = colors.textMuted, style = KilnType.note)
            }
        }
        context.helperText?.let { Text(it, color = colors.textMuted, style = KilnType.note) }
        content()
        if (error != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(DemoIcons.error, null, Modifier.size(16.dp), tint = colors.error)
                Text(error, color = colors.error, style = KilnType.note.copy(fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
internal fun KilnTextField(context: FieldContext) {
    KilnFieldFrame(context) { UnderlineInput(context, KilnType.body) }
}
