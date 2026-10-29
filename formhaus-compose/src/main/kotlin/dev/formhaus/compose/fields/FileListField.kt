package dev.formhaus.compose.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.formhaus.compose.components.FormButton
import dev.formhaus.compose.components.FormButtonVariant
import dev.formhaus.compose.components.OnFormhausFocus
import dev.formhaus.compose.components.PickerInput
import dev.formhaus.compose.form.FieldContext
import dev.formhaus.compose.model.requestFocus
import dev.formhaus.compose.theme.LocalFormhausTokens
import dev.formhaus.compose.theme.TextRole
import dev.formhaus.compose.theme.textStyle
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.renderer.fileItems
import java.io.File

@Composable
internal fun FileListField(context: FieldContext) {
    val tokens = LocalFormhausTokens.current
    val picker = rememberFilePicker(context, context::addFiles)
    val files = context.model.ownedFiles(context.value)
    val choose = context.placeholder ?: context.text("chooseFile")
    PickerInput(
        context,
        picker.active,
        false,
        JsonValue.Null,
        files.joinToString(", ") { it.file.name }.ifEmpty { choose },
        picker::open,
        below = {
            if (files.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = tokens.spacing.s3)) {
                    for ((position, file) in files.withIndex()) FileRow(context, file, position, files.size)
                }
            }
        },
    ) {
        FilePickerLabel(choose, true, tokens.colors.control)
    }
}

@Composable
private fun FileRow(
    context: FieldContext,
    file: PickedFile,
    position: Int,
    count: Int,
) {
    val tokens = LocalFormhausTokens.current
    val requester = remember { FocusRequester() }
    OnFormhausFocus(context.model.field(context.removeKey(position)), context.model, context.isLocked) { requester.requestFocus() }
    val name = file.file.name
    val remove = context.text("removeItem")
    Row(
        Modifier.fillMaxWidth().heightIn(min = tokens.controls.hitTarget),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            Modifier.weight(1f),
            color = tokens.colors.text,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            style = tokens.textStyle(TextRole.body, colored = false),
        )
        FormButton(
            remove,
            FormButtonVariant.TEXT,
            { context.removeFile(file, position, count) },
            Modifier
                .focusRequester(requester)
                .semantics {
                    contentDescription =
                        context.text(
                            "removeItemNamed",
                            stringMapOf("label" to JsonValue.String(remove), "item" to JsonValue.String(name)),
                        )
                },
            enabled = !context.isLocked,
            compact = true,
        )
    }
}

private fun FieldContext.removeKey(position: Int): String = "$key#remove$position"

internal fun FieldContext.addFiles(files: List<File>) {
    setValue(JsonValue.Array(fileItems(state.value) + files.map { JsonValue.String(fileValue(it)) }))
    announce("itemAdded", files.joinToString(", ") { it.name })
}

private fun FieldContext.removeFile(
    file: PickedFile,
    position: Int,
    count: Int,
) {
    val items = fileItems(state.value)
    if (items.getOrNull(file.index) != file.value) return
    setValue(JsonValue.Array(items.filterIndexed { index, _ -> index != file.index }))
    if (file.value !in fileItems(state.value)) UploadStore.discard(fileValue(file.file), model.uploadOwner)
    announce("itemRemoved", file.file.name)
    model.requestFocus(
        when {
            position < count - 1 -> removeKey(position)
            position > 0 -> removeKey(position - 1)
            else -> key
        },
    )
}

private fun FieldContext.announce(
    label: String,
    item: String,
) {
    model.alerts.announce(text(label, stringMapOf("item" to JsonValue.String(item))))
}
