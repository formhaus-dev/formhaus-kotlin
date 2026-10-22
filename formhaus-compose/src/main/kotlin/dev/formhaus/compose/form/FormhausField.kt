package dev.formhaus.compose.form

import androidx.compose.runtime.Composable
import dev.formhaus.compose.fields.BuiltInField
import dev.formhaus.compose.fields.UnsupportedField
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.system.SystemField
import dev.formhaus.core.model.FormField

@Composable
public fun FormhausField(
    field: FormField,
    model: FormModel,
    key: String = field.key,
) {
    val components = LocalFormComponents.current
    val context = FieldContext(field, key, model.field(key), model)
    val custom = components.fields[field.type]
    when {
        custom != null -> custom(context)
        components.system -> SystemField(context)
        components.builtIns -> BuiltInField(context)
        else -> UnsupportedField(field.type)
    }
}
