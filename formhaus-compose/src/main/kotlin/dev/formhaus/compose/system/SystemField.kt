package dev.formhaus.compose.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.formhaus.compose.components.marked
import dev.formhaus.compose.fields.AutocompleteField
import dev.formhaus.compose.fields.DateField
import dev.formhaus.compose.fields.FileField
import dev.formhaus.compose.fields.MultiselectField
import dev.formhaus.compose.fields.RadioField
import dev.formhaus.compose.fields.RatingField
import dev.formhaus.compose.fields.ScaleField
import dev.formhaus.compose.fields.UnsupportedField
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.form.LocalFormBehavior
import dev.formhaus.compose.form.formhausScrollAnchor
import dev.formhaus.compose.model.formhausTestId
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.core.renderer.hasPictureOptions

@Composable
internal fun SystemField(context: FieldContext) {
    val limit = LocalFormBehavior.current.inlineChoiceLimit
    val type = context.field.type
    when {
        type in textTypes -> SystemTextField(context)
        type == "autocomplete" -> AutocompleteField(context)
        type == "radio" && context.field.hasPictureOptions -> RadioField(context)
        type == "multiselect" && context.field.hasPictureOptions -> MultiselectField(context)
        type == "radio" && context.options.size > limit -> SystemSelect(context)
        type == "radio" || type == "multiselect" -> SystemChoiceList(context)
        type == "select" -> SystemSelect(context)
        type == "checkbox" -> SystemToggle(context, switch = false)
        type == "switch" -> SystemToggle(context, switch = true)
        type == "date" || type == "time" || type == "datetime" -> DateField(context)
        type == "file" -> FileField(context)
        type == "scale" -> ScaleField(context)
        type == "rating" -> RatingField(context)
        type == "hidden" -> Unit
        else -> UnsupportedField(type)
    }
}

private val textTypes = setOf("text", "email", "phone", "number", "password", "textarea")

@Composable
internal fun SystemBlock(
    context: FieldContext,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .formhausTestId(context.testId)
            .formhausScrollAnchor(context),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            LocalFormhausTokens.current.marked(context.label, context.isRequired),
            Modifier.semantics {
                heading()
                contentDescription = context.accessibilityName
            },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
        )
        content()
        SystemNotes(context)
    }
}

@Composable
internal fun SystemNotes(context: FieldContext) {
    context.helperText?.let {
        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    context.error?.takeIf { it.isNotEmpty() }?.let { SystemErrorText(it) }
}

@Composable
internal fun SystemErrorText(text: String) {
    Text(
        text,
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = LocalFormhausTokens.current.colors.error,
        style = MaterialTheme.typography.bodySmall,
    )
}
