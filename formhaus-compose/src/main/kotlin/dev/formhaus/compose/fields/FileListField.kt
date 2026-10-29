package dev.formhaus.compose.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.formhaus.core.renderer.itemLimits
import dev.formhaus.core.renderer.maxItemsMessage
import dev.formhaus.core.validation.fileItems
import java.io.File

@Composable
internal fun FileListField(context: FieldContext) {
    val tokens = LocalFormhausTokens.current
    val room = context.fileRoom
    var notice by remember { mutableStateOf<String?>(null) }
    val picker = rememberFilePicker(context, room) { files, dropped -> notice = context.addFiles(files, dropped) }
    val files = context.model.fileEntries(context.value)
    val choose = context.placeholder ?: context.text("chooseFile")
    PickerInput(
        context,
        picker.active,
        false,
        JsonValue.Null,
        files.joinToString(", ") { it.name }.ifEmpty { choose },
        picker::open,
        enabled = room > 0,
        below = {
            if (files.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = tokens.spacing.s3)) {
                    for ((position, file) in files.withIndex()) {
                        FileRow(context, file, position, files.size) { notice = null }
                    }
                }
            }
            notice?.let { Text(it, Modifier.padding(top = tokens.spacing.s1), style = tokens.textStyle(TextRole.error)) }
        },
    ) {
        FilePickerLabel(choose, true, tokens.colors.control)
    }
}

@Composable
private fun FileRow(
    context: FieldContext,
    file: FileEntry,
    position: Int,
    count: Int,
    removed: () -> Unit,
) {
    val tokens = LocalFormhausTokens.current
    val requester = remember { FocusRequester() }
    OnFormhausFocus(context.model.field(context.removeKey(position)), context.model, context.isLocked) { requester.requestFocus() }
    val remove = context.text("removeItem")
    Row(
        Modifier.fillMaxWidth().heightIn(min = tokens.controls.hitTarget),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            file.name,
            Modifier.weight(1f),
            color = if (file.file == null) tokens.colors.textMuted else tokens.colors.text,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            style = tokens.textStyle(TextRole.body, colored = false),
        )
        FormButton(
            remove,
            FormButtonVariant.TEXT,
            {
                context.removeFile(file, position, count)
                removed()
            },
            Modifier
                .focusRequester(requester)
                .semantics {
                    contentDescription =
                        context.text(
                            "removeItemNamed",
                            stringMapOf("label" to JsonValue.String(remove), "item" to JsonValue.String(file.name)),
                        )
                },
            enabled = !context.isLocked,
            compact = true,
        )
    }
}

private val FieldContext.fileRoom: Int
    get() = (model.engine.itemLimits(field).max - fileItems(state.value).size).coerceAtLeast(0)

private fun FieldContext.removeKey(position: Int): String = "$key#remove$position"

internal fun FieldContext.addFiles(
    files: List<File>,
    dropped: Int,
): String? {
    if (files.isNotEmpty()) setValue(JsonValue.Array(fileItems(state.value) + files.map { JsonValue.String(fileValue(it)) }))
    val added = files.takeIf { it.isNotEmpty() }?.let { label("itemAdded", it.joinToString(", ") { file -> file.name }) }
    val limit = if (dropped > 0) model.engine.maxItemsMessage(field) else null
    model.alerts.announce(listOfNotNull(added, limit).joinToString(" "))
    if (files.isNotEmpty() && fileRoom <= 0) model.requestFocus(removeKey(model.fileEntries(state.value).size - 1))
    return limit
}

private fun FieldContext.removeFile(
    file: FileEntry,
    position: Int,
    count: Int,
) {
    val items = fileItems(state.value)
    if (items.getOrNull(file.index) != file.value) return
    setValue(JsonValue.Array(items.filterIndexed { index, _ -> index != file.index }))
    if (file.file != null && file.value !in fileItems(state.value)) UploadStore.discard(fileValue(file.file), model.uploadOwner)
    model.alerts.announce(label("itemRemoved", file.name))
    model.requestFocus(
        when {
            position < count - 1 -> removeKey(position)
            position > 0 -> removeKey(position - 1)
            else -> key
        },
    )
}

private fun FieldContext.label(
    name: String,
    item: String,
): String = text(name, stringMapOf("item" to JsonValue.String(item)))
