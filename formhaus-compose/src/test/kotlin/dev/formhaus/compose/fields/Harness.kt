package dev.formhaus.compose.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.theme.FormhausTheme
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.ResolveFormhausTokens

fun definition(vararg fields: String): String =
    """{"id": "f", "title": "F", "submit": {"label": "Send"}, "fields": [${fields.joinToString()}]}"""

@Composable
fun Themed(
    theme: FormhausTheme = FormhausTheme.standard,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FormhausTheme(theme) {
        ResolveFormhausTokens(system = false) {
            Box(modifier.background(LocalFormhausTokens.current.colors.background)) { content() }
        }
    }
}

@Composable
fun FieldList(model: FormModel) {
    Column(verticalArrangement = Arrangement.spacedBy(LocalFormhausTokens.current.spacing.gap)) {
        for (field in model.structure.visibleFields) BuiltInField(FieldContext(field, model))
    }
}
