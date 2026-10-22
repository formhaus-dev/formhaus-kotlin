package dev.formhaus.compose.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import dev.formhaus.compose.components.FormhausIcons
import dev.formhaus.compose.components.PickerInput
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.json.JsonValue
import java.time.LocalDateTime

private const val SUMMARY_LINES = 2

@Composable
public fun DateField(context: FieldContext) {
    val tokens = LocalFormhausTokens.current
    val colors = tokens.colors
    val locale = LocalConfiguration.current.locales[0]
    var picking by remember { mutableStateOf(false) }
    val kind = DateKind.of(context.field.type)
    val date = if (context.text.isEmpty()) null else kind.date(context.text)
    val display = kind.display(context.text, locale)
    PickerInput(context, picking, date != null, JsonValue.String(""), display.orEmpty(), { picking = true }) {
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s3), verticalAlignment = Alignment.CenterVertically) {
            Text(
                display ?: context.placeholder ?: kind.pattern(locale),
                Modifier.weight(1f),
                color =
                    when {
                        display == null -> colors.control
                        context.isLocked -> colors.textMuted
                        else -> colors.text
                    },
                maxLines = SUMMARY_LINES,
                style = tokens.textStyle(TextRole.body, colored = false),
            )
            if (display == null) {
                Icon(
                    if (kind == DateKind.TIME) FormhausIcons.clock else FormhausIcons.calendar,
                    null,
                    Modifier.size(tokens.controls.indicator),
                    tint = colors.textMuted,
                )
            }
        }
    }
    if (picking) {
        DatePickerPanel(kind, date ?: LocalDateTime.now(), context.text("done"), { picking = false }) {
            context.setValue(JsonValue.String(kind.text(it)))
            picking = false
        }
    }
}
